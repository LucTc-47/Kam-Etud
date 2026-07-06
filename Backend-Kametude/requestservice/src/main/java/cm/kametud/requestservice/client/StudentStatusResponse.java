package cm.kametud.requestservice.client;

public record StudentStatusResponse(Boolean verified, Boolean banned) {
    public boolean canPropose() {
        return Boolean.TRUE.equals(verified) && !Boolean.TRUE.equals(banned);
    }
}
