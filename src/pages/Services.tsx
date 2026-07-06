import { useState, useCallback, useEffect } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import Layout from "@/components/layout/Layout";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { motion } from "framer-motion";
import {
  Search, MapPin, Star, Filter, GraduationCap, Monitor, Home, Truck, Wrench, PartyPopper, Sparkles,
  Map, List, Navigation, Loader2,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useToast } from "@/hooks/use-toast";
import ServiceMap from "@/components/services/ServiceMap";
import { useGigs, useCities, useCategories } from "@/hooks/useBackendData";
import { useLanguage } from "@/contexts/LanguageContext";
import SEO from "@/components/seo/SEO";


const categoryIcons: Record<string, LucideIcon> = {
  "Académique": GraduationCap, "Numérique": Monitor, "Aide à domicile": Home,
  "Livraison & Courses": Truck, "Bricolage": Wrench, "Événementiel": PartyPopper, "Beauté & Bien-être": Sparkles,
};

function haversineKm(lat1: number, lng1: number, lat2: number, lng2: number) {
  const R = 6371;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLng = ((lng2 - lng1) * Math.PI) / 180;
  const a = Math.sin(dLat / 2) ** 2 + Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) * Math.sin(dLng / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

const Services = () => {
  const { t } = useLanguage();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { toast } = useToast();

  const initialCat = searchParams.get("categorie") || "";
  const [selectedCategory, setSelectedCategory] = useState(initialCat);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedCity, setSelectedCity] = useState("");
  const [minRating, setMinRating] = useState("");
  const [viewMode, setViewMode] = useState<"list" | "map">("list");
  const [userLocation, setUserLocation] = useState<{ lat: number; lng: number } | null>(null);
  const [maxDistance, setMaxDistance] = useState("");
  const [geoLoading, setGeoLoading] = useState(false);

  // Debounced search query for API call
  const [debouncedQuery, setDebouncedSearch] = useState("");

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchQuery);
    }, 500);
    return () => clearTimeout(timer);
  }, [searchQuery]);

  const { data: dbGigs, isLoading: gigsLoading } = useGigs(debouncedQuery);
  const { data: dbCities } = useCities();
  const { data: dbCategories } = useCategories();

  const allGigs = dbGigs || [];
  const cities = dbCities ? dbCities.map(c => c.name) : [];
  const allCategories = dbCategories
    ? dbCategories.filter(c => c.active).map(c => c.name)
    : Object.keys(categoryIcons);

  const handleGeolocate = () => {
    if (!navigator.geolocation) { toast({ title: t.sv_geo_unavail, variant: "destructive" }); return; }
    setGeoLoading(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => { setUserLocation({ lat: pos.coords.latitude, lng: pos.coords.longitude }); setGeoLoading(false); toast({ title: t.sv_geo_ok }); },
      () => { setGeoLoading(false); toast({ title: t.sv_geo_err, variant: "destructive" }); },
      { enableHighAccuracy: true, timeout: 10000 }
    );
  };

  const filtered = allGigs.filter((g) => {
    if (!g.active) return false;
    const matchCat = !selectedCategory || g.category === selectedCategory;
    const matchCity = !selectedCity || g.location === selectedCity;
    const matchRating = !minRating || g.rating >= parseFloat(minRating);
    let matchDistance = true;
    if (maxDistance && userLocation && g.gpsLocation) {
      matchDistance = haversineKm(userLocation.lat, userLocation.lng, g.gpsLocation.lat, g.gpsLocation.lng) <= parseFloat(maxDistance);
    }
    return matchCat && matchCity && matchRating && matchDistance;
  }).sort((a, b) => {
    if (userLocation && a.gpsLocation && b.gpsLocation) {
      return haversineKm(userLocation.lat, userLocation.lng, a.gpsLocation.lat, a.gpsLocation.lng) - haversineKm(userLocation.lat, userLocation.lng, b.gpsLocation.lat, b.gpsLocation.lng);
    }
    return 0;
  });

  const getDistance = (gig: typeof allGigs[0]) => {
    if (!userLocation || !gig.gpsLocation) return null;
    return haversineKm(userLocation.lat, userLocation.lng, gig.gpsLocation.lat, gig.gpsLocation.lng);
  };

  const handleMapGigClick = useCallback((gigId: string) => { navigate(`/commander/${gigId}`); }, [navigate]);

  return (
    <Layout>
      <SEO title={t.sv_title1} description={t.sv_subtitle} />
      <section className="py-10 md:py-16">
        <div className="container mx-auto px-4">
          <div className="text-center mb-10">
            <h1 className="font-display text-3xl md:text-4xl font-bold text-foreground">
              {t.sv_title1} <span className="text-gradient-primary">{t.sv_title2}</span>
            </h1>
            <p className="mt-3 text-muted-foreground">{t.sv_subtitle}</p>
          </div>

          <div className="max-w-4xl mx-auto mb-8 space-y-4">
            <div className="relative">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground" />
              <Input placeholder={t.sv_search} className="pl-12 h-12 text-base" value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} />
            </div>
            <div className="flex flex-wrap gap-3 items-center">
              <Select value={selectedCity} onValueChange={(value) => setSelectedCity(value === "all" ? "" : value)}>
                <SelectTrigger className="w-[160px]"><MapPin className="w-4 h-4 mr-1 text-muted-foreground" /><SelectValue placeholder={t.sv_city} /></SelectTrigger>
                <SelectContent><SelectItem value="all">{t.sv_all}</SelectItem>{cities.map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}</SelectContent>
              </Select>
              <Select value={minRating} onValueChange={(value) => setMinRating(value === "all" ? "" : value)}>
                <SelectTrigger className="w-[160px]"><Star className="w-4 h-4 mr-1 text-muted-foreground" /><SelectValue placeholder={t.sv_rating} /></SelectTrigger>
                <SelectContent><SelectItem value="all">{t.sv_all}</SelectItem><SelectItem value="4.5">4.5+</SelectItem><SelectItem value="4.0">4.0+</SelectItem><SelectItem value="3.5">3.5+</SelectItem></SelectContent>
              </Select>
              <Button variant={userLocation ? "default" : "outline"} size="sm" onClick={handleGeolocate} disabled={geoLoading} className={userLocation ? "bg-gradient-hero" : ""}>
                {geoLoading ? <Loader2 className="w-4 h-4 mr-1 animate-spin" /> : <Navigation className="w-4 h-4 mr-1" />}
                {userLocation ? t.sv_geo_loc : t.sv_geo_btn}
              </Button>
              {userLocation && (
                <Select value={maxDistance} onValueChange={(value) => setMaxDistance(value === "all" ? "" : value)}>
                  <SelectTrigger className="w-[150px]"><SelectValue placeholder={t.sv_distance} /></SelectTrigger>
                  <SelectContent><SelectItem value="all">{t.sv_all}</SelectItem><SelectItem value="5">≤ 5km</SelectItem><SelectItem value="10">≤ 10km</SelectItem><SelectItem value="25">≤ 25km</SelectItem><SelectItem value="50">≤ 50km</SelectItem></SelectContent>
                </Select>
              )}
              <div className="ml-auto flex gap-1 border border-border rounded-lg p-0.5">
                <Button size="sm" variant={viewMode === "list" ? "default" : "ghost"} className={viewMode === "list" ? "bg-gradient-hero" : ""} onClick={() => setViewMode("list")}><List className="w-4 h-4" /></Button>
                <Button size="sm" variant={viewMode === "map" ? "default" : "ghost"} className={viewMode === "map" ? "bg-gradient-hero" : ""} onClick={() => setViewMode("map")}><Map className="w-4 h-4" /></Button>
              </div>
              {(selectedCity || minRating || selectedCategory || searchQuery || maxDistance) && (
                <Button variant="ghost" size="sm" onClick={() => { setSelectedCity(""); setMinRating(""); setSelectedCategory(""); setSearchQuery(""); setMaxDistance(""); }}>{t.sv_reset}</Button>
              )}
            </div>
          </div>

          <div className="flex flex-wrap justify-center gap-2 mb-10">
            <Button size="sm" variant={selectedCategory === "" ? "default" : "outline"} className={selectedCategory === "" ? "bg-gradient-hero" : ""} onClick={() => setSelectedCategory("")}>
              <Filter className="w-4 h-4 mr-1" /> {t.sv_all}
            </Button>
            {allCategories.map((cat) => {
              const Icon = categoryIcons[cat] || Filter;
              return (
                <Button key={cat} size="sm" variant={selectedCategory === cat ? "default" : "outline"} className={selectedCategory === cat ? "bg-gradient-hero" : ""} onClick={() => setSelectedCategory(cat)}>
                  <Icon className="w-4 h-4 mr-1" /> {cat}
                </Button>
              );
            })}
          </div>

          {viewMode === "map" && (
            <div className="mb-8">
              <ServiceMap gigs={filtered} userLocation={userLocation} onGigClick={handleMapGigClick} />
              <p className="text-xs text-muted-foreground text-center mt-2">{filtered.length} {filtered.length > 1 ? t.sv_results_plural : t.sv_results}</p>
            </div>
          )}

          {viewMode === "list" && (
            <>
              {gigsLoading && <div className="text-center py-16"><Loader2 className="w-8 h-8 animate-spin mx-auto text-primary" /></div>}
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
                {filtered.map((gig, i) => {
                  const dist = getDistance(gig);
                  return (
                    <motion.div key={gig.id} initial={{ opacity: 0, y: 15 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.05 }}
                      className="rounded-2xl bg-card border border-border shadow-card hover:shadow-elevated transition-all duration-300 hover:-translate-y-1 overflow-hidden cursor-pointer"
                      onClick={() => navigate(`/commander/${gig.id}`)}>
                      <div className="h-2 bg-gradient-hero" />
                      <div className="p-6">
                        <div className="flex items-start justify-between gap-3 mb-3">
                          <Badge variant="secondary" className="text-xs">{gig.category}</Badge>
                          <Badge variant={gig.badge === "Expert" ? "default" : "outline"} className={gig.badge === "Expert" ? "bg-gradient-gold text-foreground text-xs" : "text-xs"}>{gig.badge}</Badge>
                        </div>
                        <h3 className="font-display font-semibold text-foreground text-lg leading-snug">{gig.title}</h3>
                        <div className="mt-3 flex items-center gap-3 text-sm text-muted-foreground">
                          <span className="font-medium text-foreground">{gig.studentName}</span>
                          <span className="flex items-center gap-1"><Star className="w-4 h-4 text-secondary fill-secondary" />{gig.rating}</span>
                          <span>({gig.reviewCount} {t.sv_reviews})</span>
                        </div>
                        <div className="mt-4 flex items-center justify-between">
                          <div className="flex items-center gap-2">
                            <div className="flex items-center gap-1 text-sm text-muted-foreground"><MapPin className="w-4 h-4" />{gig.location}</div>
                            {dist !== null && <Badge variant="outline" className="text-xs"><Navigation className="w-3 h-3 mr-0.5" />{dist < 1 ? `${Math.round(dist * 1000)}m` : `${dist.toFixed(1)}km`}</Badge>}
                          </div>
                          <div className="text-right">
                            <span className="text-xs text-muted-foreground">{t.sv_from}</span>
                            <p className="font-display font-bold text-primary text-lg">{gig.tiers.basique.price.toLocaleString()} FCFA</p>
                          </div>
                        </div>
                      </div>
                    </motion.div>
                  );
                })}
              </div>
              {!gigsLoading && filtered.length === 0 && <div className="text-center py-16 text-muted-foreground"><p className="text-lg">{t.sv_none}</p></div>}
            </>
          )}
        </div>
      </section>
    </Layout>
  );
};

export default Services;
