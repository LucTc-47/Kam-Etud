import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { Menu, X, User, LogOut, Settings, ShoppingBag, Briefcase, LayoutDashboard, Sun, Moon, Globe, Coins } from "lucide-react";
import { Button } from "@/components/ui/button";
import { motion, AnimatePresence } from "framer-motion";
import { useAuth } from "@/contexts/AuthContext";
import { useTheme } from "@/contexts/ThemeContext";
import { useLanguage } from "@/contexts/LanguageContext";
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem,
  DropdownMenuSeparator, DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import NotificationBell from "./NotificationBell";

const Navbar = () => {
  const [isOpen, setIsOpen] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();
  const { user, isAuthenticated, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const { language, setLanguage, t } = useLanguage();

  const navLinks = [
    { href: "/", label: t.home },
    { href: "/services", label: t.services },
    { href: "/demandes", label: t.requests },
    { href: "/comment-ca-marche", label: t.howItWorks },
  ];

  const handleLogout = () => { logout(); navigate("/"); };

  const roleMenuItems = () => {
    if (!user) return [];
    switch (user.role) {
      case 'client': return [
        { label: t.myOrders, href: "/mes-commandes", icon: ShoppingBag },
        { label: t.myRequests, href: "/mes-demandes", icon: Briefcase },
      ];
      case 'student': return [
        { label: t.myMissions, href: "/mes-missions", icon: Briefcase },
        { label: "Mes propositions", href: "/mes-propositions", icon: Coins },
        { label: t.myGigs, href: "/mes-gigs", icon: Settings },
        { label: t.myProfile, href: `/profil/${user.id}`, icon: User },
      ];
      case 'admin': return [
        { label: t.adminDashboard, href: "/admin", icon: LayoutDashboard },
      ];
      case 'moderator': return [
        { label: t.modDashboard, href: "/moderateur", icon: LayoutDashboard },
      ];
      default: return [];
    }
  };

  const roleName = (role: string) => {
    const map: Record<string, string> = { student: t.student, moderator: t.moderator, client: t.client, admin: t.admin };
    return map[role] || role;
  };

  return (
    <nav className="fixed top-0 left-0 right-0 z-50 bg-background/80 backdrop-blur-xl border-b border-border">
      <div className="container mx-auto px-4 h-16 flex items-center justify-between">
        <Link to="/" className="flex items-center gap-2">
          <div className="w-9 h-9 rounded-lg bg-gradient-hero flex items-center justify-center">
            <span className="text-primary-foreground font-display font-bold text-lg">K</span>
          </div>
          <span className="font-display font-bold text-xl text-foreground">
            Kam'<span className="text-gradient-primary">Etud</span>
          </span>
        </Link>

        <div className="hidden md:flex items-center gap-8">
          {navLinks.map((link) => (
            <Link key={link.href} to={link.href} className={`text-sm font-medium transition-colors hover:text-primary ${location.pathname === link.href ? "text-primary" : "text-muted-foreground"}`}>
              {link.label}
            </Link>
          ))}
        </div>

        <div className="hidden md:flex items-center gap-2">
          {/* Language toggle */}
          <Button variant="ghost" size="icon" className="w-8 h-8" onClick={() => setLanguage(language === 'fr' ? 'en' : 'fr')} title={language === 'fr' ? 'Switch to English' : 'Passer en français'}>
            <Globe className="w-4 h-4" />
          </Button>
          {/* Theme toggle */}
          <Button variant="ghost" size="icon" className="w-8 h-8" onClick={toggleTheme} title={theme === 'light' ? t.darkMode : t.lightMode}>
            {theme === 'light' ? <Moon className="w-4 h-4" /> : <Sun className="w-4 h-4" />}
          </Button>

          {isAuthenticated && <NotificationBell />}

          {isAuthenticated && user ? (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="sm" className="gap-2">
                  <Avatar className="w-7 h-7">
                    <AvatarFallback className="bg-gradient-hero text-primary-foreground text-xs">
                      {user.firstName[0]}{user.lastName?.[0] || ''}
                    </AvatarFallback>
                  </Avatar>
                  <span className="text-sm">{user.firstName}</span>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-48">
                <div className="px-2 py-1.5">
                  <p className="text-sm font-medium text-foreground">{user.firstName} {user.lastName}</p>
                  <p className="text-xs text-muted-foreground capitalize">{roleName(user.role)}</p>
                </div>
                <DropdownMenuSeparator />
                {roleMenuItems().map((item) => (
                  <DropdownMenuItem key={item.href} onClick={() => navigate(item.href)}>
                    <item.icon className="w-4 h-4 mr-2" /> {item.label}
                  </DropdownMenuItem>
                ))}
                <DropdownMenuSeparator />
                <DropdownMenuItem onClick={handleLogout} className="text-destructive">
                  <LogOut className="w-4 h-4 mr-2" /> {t.logout}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          ) : (
            <>
              <Button variant="ghost" size="sm" asChild><Link to="/connexion">{t.login}</Link></Button>
              <Button size="sm" className="bg-gradient-hero hover:opacity-90" asChild><Link to="/inscription">{t.register}</Link></Button>
            </>
          )}
        </div>

        <div className="flex md:hidden items-center gap-1">
          <Button variant="ghost" size="icon" className="w-8 h-8" onClick={() => setLanguage(language === 'fr' ? 'en' : 'fr')}>
            <Globe className="w-4 h-4" />
          </Button>
          <Button variant="ghost" size="icon" className="w-8 h-8" onClick={toggleTheme}>
            {theme === 'light' ? <Moon className="w-4 h-4" /> : <Sun className="w-4 h-4" />}
          </Button>
          {isAuthenticated && <NotificationBell />}
          <button className="p-2" onClick={() => setIsOpen(!isOpen)}>
            {isOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
          </button>
        </div>
      </div>

      <AnimatePresence>
        {isOpen && (
          <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }} className="md:hidden bg-background border-b border-border overflow-hidden">
            <div className="container mx-auto px-4 py-4 flex flex-col gap-3">
              {navLinks.map((link) => (
                <Link key={link.href} to={link.href} onClick={() => setIsOpen(false)} className={`text-sm font-medium py-2 ${location.pathname === link.href ? "text-primary" : "text-muted-foreground"}`}>
                  {link.label}
                </Link>
              ))}
              {isAuthenticated && user ? (
                <>
                  {roleMenuItems().map((item) => (
                    <Link key={item.href} to={item.href} onClick={() => setIsOpen(false)} className="text-sm font-medium py-2 text-muted-foreground">
                      {item.label}
                    </Link>
                  ))}
                  <Button variant="outline" size="sm" onClick={() => { handleLogout(); setIsOpen(false); }}>
                    <LogOut className="w-4 h-4 mr-1" /> {t.logout}
                  </Button>
                </>
              ) : (
                <div className="flex gap-3 pt-2">
                  <Button variant="outline" size="sm" className="flex-1" asChild><Link to="/connexion">{t.login}</Link></Button>
                  <Button size="sm" className="flex-1 bg-gradient-hero" asChild><Link to="/inscription">{t.register}</Link></Button>
                </div>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </nav>
  );
};

export default Navbar;
