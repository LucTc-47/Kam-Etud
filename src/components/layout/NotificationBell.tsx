import { Bell, Check, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Badge } from "@/components/ui/badge";
import { useNotifications } from "@/contexts/NotificationContext";
import { useNavigate } from "react-router-dom";
import { useLanguage } from "@/contexts/LanguageContext";
import type { AppNotification } from "@/contexts/NotificationContext";

const NotificationBell = () => {
  const { notifications, unreadCount, markAsRead, markAllAsRead, clearAll } = useNotifications();
  const navigate = useNavigate();
  const { t, locale } = useLanguage();

  const handleNotificationClick = (n: AppNotification) => {
    markAsRead(n.id);
    navigate(n.link);
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className="relative w-8 h-8">
          <Bell className="w-4 h-4" />
          {unreadCount > 0 && (
            <Badge className="absolute -top-1 -right-1 w-4 h-4 p-0 flex items-center justify-center bg-destructive text-[10px]">
              {unreadCount}
            </Badge>
          )}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-80 p-0">
        <div className="p-3 border-b border-border flex items-center justify-between">
          <span className="font-display font-bold text-sm">{t.nt_title}</span>
          <div className="flex gap-2">
            <button onClick={markAllAsRead} className="text-xs text-primary hover:underline flex items-center gap-1">
              <Check className="w-3 h-3" /> {t.nt_mark_read}
            </button>
            <button onClick={clearAll} className="text-xs text-muted-foreground hover:text-destructive transition-colors">
              <Trash2 className="w-3 h-3" />
            </button>
          </div>
        </div>
        <div className="max-h-96 overflow-y-auto">
          {notifications.length === 0 ? (
            <div className="p-8 text-center text-sm text-muted-foreground">
              {t.nt_none}
            </div>
          ) : (
            notifications.map((n) => (
              <DropdownMenuItem
                key={n.id}
                className={`p-4 border-b border-border/50 cursor-pointer flex flex-col items-start gap-1 focus:bg-muted ${!n.read ? "bg-primary/5" : ""}`}
                onClick={() => handleNotificationClick(n)}
              >
                <div className="flex items-center justify-between w-full">
                  <span className="font-bold text-xs">{n.title}</span>
                  <span className="text-[10px] text-muted-foreground">
                    {new Date(n.createdAt).toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' })}
                  </span>
                </div>
                <p className="text-xs text-muted-foreground line-clamp-2">{n.description}</p>
                {!n.read && <div className="w-1.5 h-1.5 rounded-full bg-primary mt-1" />}
              </DropdownMenuItem>
            ))
          )}
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  );
};

export default NotificationBell;
