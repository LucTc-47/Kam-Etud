param(
    [string]$ApiBase = "http://localhost:8080",
    [string]$InternalToken = "change-this-internal-token",
    [string]$Password = "123456789!"
)

$ErrorActionPreference = "Stop"
$suffix = Get-Date -Format "yyyyMMddHHmmss"
$demoFile = Join-Path $PSScriptRoot "..\support-service\demo-files\kyc-demo.svg"

function Assert-That([bool]$condition, [string]$message) {
    if (-not $condition) { throw "ECHEC: $message" }
    Write-Host "[OK] $message" -ForegroundColor Green
}

function Get-AuthHeaders([string]$token) {
    return @{ Authorization = "Bearer $token" }
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token,
        $Body
    )

    $parameters = @{
        Method = $Method
        Uri = "$ApiBase$Path"
    }
    if ($Token) { $parameters.Headers = Get-AuthHeaders $Token }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = $Body | ConvertTo-Json -Depth 10
    }
    return Invoke-RestMethod @parameters
}

function Get-HttpStatus {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token,
        $Body
    )

    $parameters = @{
        Method = $Method
        Uri = "$ApiBase$Path"
        UseBasicParsing = $true
    }
    if ($Token) { $parameters.Headers = Get-AuthHeaders $Token }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = $Body | ConvertTo-Json -Depth 10
    }
    try {
        return [int](Invoke-WebRequest @parameters).StatusCode
    } catch {
        if ($_.Exception.Response) {
            return [int]$_.Exception.Response.StatusCode
        }
        throw
    }
}

function Login([string]$email) {
    return Invoke-Api -Method POST -Path "/api/auth/login" -Body @{
        email = $email
        password = $Password
    }
}

function New-Tier([string]$name, [int]$price, [int]$days) {
    return @{
        name = $name
        description = "Palier de test $name"
        price = $price
        deliveryDays = $days
        features = @("Test local", "Livraison controlee")
    }
}

function Confirm-PaymentHeld([string]$orderId) {
    return Invoke-RestMethod -Method POST `
        -Uri "http://localhost:8084/api/orders/internal/$orderId/payment-held" `
        -Headers @{ "X-Internal-Service-Token" = $InternalToken }
}

function Upload-PrivateFile([string]$token, [string]$orderId) {
    $raw = & curl.exe -sS -X POST "$ApiBase/api/storage/upload" `
        -H "Authorization: Bearer $token" `
        -F "file=@$demoFile;type=image/svg+xml" `
        -F "visibility=private" `
        -F "category=deliverables" `
        -F "resourceId=$orderId"
    if ($LASTEXITCODE -ne 0) { throw "Upload Support Service impossible" }
    $upload = $raw | ConvertFrom-Json
    return "$ApiBase$($upload.downloadUrl)"
}

Write-Host "=== Kam'Etud smoke test $suffix ===" -ForegroundColor Cyan

$health = Invoke-RestMethod "$ApiBase/actuator/health"
Assert-That ($health.status -eq "UP") "Gateway disponible"

$admin = Login "admin@kametud.com"
$moderator = Login "moderator@kametud.com"
$student = Login "student@kametud.com"
$client = Login "client@kametud.com"
Assert-That ($admin.role -eq "admin") "Connexion administrateur"
Assert-That ($moderator.role -eq "moderator") "Connexion moderateur"
Assert-That ($student.role -eq "student" -and $student.profile.verified) "Connexion etudiant verifie"
Assert-That ($client.role -eq "client") "Connexion client"

$adminToken = $admin.token
$moderatorToken = $moderator.token
$studentToken = $student.token
$clientToken = $client.token

$profiles = Invoke-Api GET "/api/admin/profiles" $adminToken $null
$verifications = Invoke-Api GET "/api/admin/verifications" $adminToken $null
Assert-That (@($profiles | Where-Object email -eq "student@kametud.com").Count -eq 1) "Lecture admin des profils"
Assert-That (@($verifications | Where-Object { $_.email -eq "student@kametud.com" -and $_.status -eq "approved" }).Count -ge 1) "KYC approuve visible"

$unauthenticatedOrderStatus = Get-HttpStatus GET "/api/orders/mine" "" $null
$clientGigStatus = Get-HttpStatus POST "/api/gigs" $clientToken @{}
Assert-That ($unauthenticatedOrderStatus -eq 401) "Gateway refuse une commande sans JWT"
Assert-That ($clientGigStatus -eq 403) "Gateway refuse la creation de gig au client"

$category = Invoke-Api POST "/api/categories" $adminToken @{ name = "Smoke-$suffix" }
Assert-That ($category.name -eq "Smoke-$suffix") "Creation categorie admin"
Invoke-Api DELETE "/api/categories/$($category.id)" $adminToken $null | Out-Null
Assert-That ($true) "Suppression categorie admin"

$gig = Invoke-Api POST "/api/gigs" $studentToken @{
    title = "Service smoke $suffix"
    description = "Service cree par le test local Kam'Etud"
    category = "Developpement web"
    location = "Dschang"
    tierBasique = New-Tier "Basique" 10000 7
    tierStandard = New-Tier "Standard" 20000 5
    tierPremium = New-Tier "Premium" 30000 3
    published = $true
    images = @()
}
Assert-That ($gig.published -and $gig.studentId -eq $student.profile.user_id) "Creation et publication d'un gig"
$publicGig = Invoke-Api GET "/api/gigs/$($gig.id)" "" $null
Assert-That ($publicGig.id -eq $gig.id) "Lecture publique du gig"

$deliveryOrder = Invoke-Api POST "/api/orders" $clientToken @{
    gigId = $gig.id
    tier = "standard"
    description = "Commande de livraison smoke $suffix"
    paymentMethod = "mtn"
}
Assert-That ($deliveryOrder.status -eq "pending" -and $deliveryOrder.budget -eq 20000) "Commande basee sur le prix Catalog"

$paymentStatus = Get-HttpStatus POST "/api/payments/initiate" $clientToken @{
    orderId = $deliveryOrder.id
    phone = "+237670000004"
}
Assert-That ($paymentStatus -ge 400) "Erreur MeSomb controlee avec les cles locales factices"

$acceptedOrder = Confirm-PaymentHeld $deliveryOrder.id
Assert-That ($acceptedOrder.status -eq "accepted") "Simulation locale du sequestre"
$startedOrder = Invoke-Api PATCH "/api/orders/$($deliveryOrder.id)/status" $studentToken @{ status = "in_progress" }
Assert-That ($startedOrder.status -eq "in_progress") "Demarrage de mission par l'etudiant"

$deliverableUrl = Upload-PrivateFile $studentToken $deliveryOrder.id
$deliveredOrder = Invoke-Api PATCH "/api/orders/$($deliveryOrder.id)/status" $studentToken @{
    status = "delivered"
    deliverableUrl = $deliverableUrl
    deliverableNote = "Livrable du smoke test"
}
Assert-That ($deliveredOrder.status -eq "delivered") "Livraison privee enregistree"

$download = Invoke-WebRequest -Uri $deliverableUrl -Headers (Get-AuthHeaders $clientToken) -UseBasicParsing
Assert-That ($download.StatusCode -eq 200) "Telechargement du livrable par le client"

$clientMessage = Invoke-Api POST "/api/chat/orders/$($deliveryOrder.id)/messages" $clientToken @{
    orderId = $deliveryOrder.id
    content = "Message smoke $suffix"
}
$messages = Invoke-Api GET "/api/chat/orders/$($deliveryOrder.id)/messages" $studentToken $null
Assert-That (@($messages | Where-Object id -eq $clientMessage.id).Count -eq 1) "Chat client-etudiant"

$completedOrder = Invoke-Api PATCH "/api/orders/$($deliveryOrder.id)/status" $clientToken @{ status = "completed" }
Assert-That ($completedOrder.status -eq "completed") "Validation du livrable par le client"
$review = Invoke-Api POST "/api/reviews" $clientToken @{
    orderId = $deliveryOrder.id
    rating = 5
    text = "Excellent smoke test $suffix"
}
Assert-That ($review.rating -eq 5) "Creation d'un avis"

$gigRequest = Invoke-Api POST "/api/v1/requests" $clientToken @{
    title = "Demande smoke $suffix"
    description = "Besoin cree pour verifier Request Service"
    budget = 25000
    category = "Developpement web"
    location = "Dschang"
    deadline = (Get-Date).AddDays(7).ToString("yyyy-MM-dd")
}
$proposal = Invoke-Api POST "/api/v1/proposals" $studentToken @{
    requestId = $gigRequest.id
    price = 18000
    deliveryDays = 4
    message = "Proposition smoke $suffix"
}
$acceptedProposal = Invoke-Api PUT "/api/v1/proposals/$($proposal.id)/accept" $clientToken $null
Assert-That ($acceptedProposal.status -eq "accepted") "Acceptation d'une proposition"
$clientOrders = Invoke-Api GET "/api/orders/mine" $clientToken $null
$proposalOrder = $clientOrders | Where-Object sourceProposalId -eq $proposal.id | Select-Object -First 1
Assert-That ($null -ne $proposalOrder) "Creation automatique de commande depuis Request"

$disputeOrder = Invoke-Api POST "/api/orders" $clientToken @{
    gigId = $gig.id
    tier = "basique"
    description = "Commande litige smoke $suffix"
    paymentMethod = "orange"
}
Confirm-PaymentHeld $disputeOrder.id | Out-Null
Invoke-Api PATCH "/api/orders/$($disputeOrder.id)/status" $studentToken @{ status = "in_progress" } | Out-Null
$dispute = Invoke-Api POST "/api/disputes" $clientToken @{
    orderId = $disputeOrder.id
    clientStatement = "Declaration client smoke $suffix"
}
$studentResponse = Invoke-Api PATCH "/api/disputes/order/$($disputeOrder.id)/response" $studentToken @{
    statement = "Reponse etudiant smoke $suffix"
}
Assert-That ($studentResponse.status -eq "under_review") "Reponse etudiant au litige"
$moderatorDisputes = Invoke-Api GET "/api/disputes" $moderatorToken $null
Assert-That (@($moderatorDisputes | Where-Object id -eq $dispute.id).Count -eq 1) "Lecture du litige par le moderateur"
$resolved = Invoke-Api PATCH "/api/disputes/$($dispute.id)" $moderatorToken @{
    status = "resolved_student"
    moderatorNote = "Resolution smoke $suffix"
}
Assert-That ($resolved.status -eq "resolved_student") "Resolution du litige par le moderateur"

$studentNotifications = Invoke-Api GET "/api/notifications/me" $studentToken $null
$clientNotifications = Invoke-Api GET "/api/notifications/me" $clientToken $null
Assert-That (@($studentNotifications).Count -gt 0) "Notifications etudiant"
Assert-That (@($clientNotifications).Count -gt 0) "Notifications client"

Write-Host "=== TOUS LES TESTS LOCAUX SONT PASSES ===" -ForegroundColor Cyan
Write-Host "Gig: $($gig.id)"
Write-Host "Commande livree: $($deliveryOrder.id)"
Write-Host "Demande: $($gigRequest.id)"
Write-Host "Litige: $($dispute.id)"
