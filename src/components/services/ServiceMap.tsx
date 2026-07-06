import { useEffect, useRef } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import type { Gig } from "@/types";
import { useLanguage } from "@/contexts/LanguageContext";

// Fix default marker icon
delete (L.Icon.Default.prototype as L.Icon.Default & { _getIconUrl?: unknown })._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png",
  iconUrl: "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png",
  shadowUrl: "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png",
});

interface ServiceMapProps {
  gigs: Gig[];
  userLocation?: { lat: number; lng: number } | null;
  onGigClick: (gigId: string) => void;
}

const ServiceMap = ({ gigs, userLocation, onGigClick }: ServiceMapProps) => {
  const { t } = useLanguage();
  const mapRef = useRef<HTMLDivElement>(null);
  const mapInstance = useRef<L.Map | null>(null);

  useEffect(() => {
    if (!mapRef.current || mapInstance.current) return;

    const center = userLocation
      ? [userLocation.lat, userLocation.lng] as [number, number]
      : [5.9631, 10.1591] as [number, number]; // Cameroon center

    const map = L.map(mapRef.current).setView(center, 7);
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: '&copy; <a href="https://www.openstreetmap.org/">OSM</a>',
    }).addTo(map);

    mapInstance.current = map;

    return () => {
      map.remove();
      mapInstance.current = null;
    };
  }, [userLocation]);

  useEffect(() => {
    const map = mapInstance.current;
    if (!map) return;

    // Clear existing markers
    map.eachLayer((layer) => {
      if (layer instanceof L.Marker || layer instanceof L.CircleMarker) {
        map.removeLayer(layer);
      }
    });

    // User location marker
    if (userLocation) {
      L.circleMarker([userLocation.lat, userLocation.lng], {
        radius: 10,
        fillColor: "hsl(160, 60%, 30%)",
        color: "#fff",
        weight: 2,
        fillOpacity: 0.9,
      })
        .addTo(map)
        .bindPopup(`<strong>📍 ${t.sv_map_pos}</strong>`);
    }

    // Gig markers
    gigs.forEach((gig) => {
      if (!gig.gpsLocation) return;
      const marker = L.marker([gig.gpsLocation.lat, gig.gpsLocation.lng]).addTo(map);
      marker.bindPopup(`
        <div style="min-width:180px">
          <strong>${gig.title}</strong><br/>
          <span style="color:#666">${gig.studentName} · ⭐ ${gig.rating}</span><br/>
          <span style="font-weight:bold;color:hsl(160,60%,30%)">${t.sv_from} ${gig.tiers.basique.price.toLocaleString()} FCFA</span>
        </div>
      `);
      marker.on("click", () => onGigClick(gig.id));
    });

    // Fit bounds if gigs exist
    const coords = gigs
      .filter((g) => g.gpsLocation)
      .map((g) => [g.gpsLocation!.lat, g.gpsLocation!.lng] as [number, number]);
    if (userLocation) coords.push([userLocation.lat, userLocation.lng]);
    if (coords.length > 1) {
      map.fitBounds(L.latLngBounds(coords), { padding: [30, 30] });
    } else if (coords.length === 1) {
      map.setView(coords[0], 12);
    }
  }, [gigs, userLocation, onGigClick, t.sv_from, t.sv_map_pos]);

  return <div ref={mapRef} className="w-full h-[500px] rounded-xl border border-border overflow-hidden" />;
};

export default ServiceMap;
