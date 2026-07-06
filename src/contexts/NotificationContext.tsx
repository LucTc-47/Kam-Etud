import { createContext, useContext, useEffect, useState, ReactNode } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import { api } from '@/lib/api';
import { toast } from 'sonner';

export interface AppNotification {
  id: string;
  title: string;
  description: string;
  type: 'message' | 'order' | 'proposal' | 'system';
  link: string;
  read: boolean;
  createdAt: string;
}

type ApiNotification = {
  id: string;
  title: string;
  message: string;
  type: 'ORDER_UPDATE' | 'NEW_MESSAGE' | 'PAYMENT_CONFIRMED' | 'PROPOSAL' | 'SYSTEM';
  link?: string | null;
  read: boolean;
  createdAt: string;
};

interface NotificationContextType {
  notifications: AppNotification[];
  unreadCount: number;
  markAsRead: (id: string) => void;
  markAllAsRead: () => void;
  clearAll: () => void;
}

const NotificationContext = createContext<NotificationContextType | null>(null);

function mapNotification(value: ApiNotification): AppNotification {
  const type = value.type === 'NEW_MESSAGE' ? 'message'
    : value.type === 'PROPOSAL' ? 'proposal'
      : value.type === 'ORDER_UPDATE' || value.type === 'PAYMENT_CONFIRMED' ? 'order' : 'system';
  return {
    id: value.id,
    title: value.title,
    description: value.message,
    type,
    link: value.link || '/',
    read: value.read,
    createdAt: value.createdAt,
  };
}

export const NotificationProvider = ({ children }: { children: ReactNode }) => {
  const { user } = useAuth();
  const [notifications, setNotifications] = useState<AppNotification[]>([]);

  useEffect(() => {
    if (!user?.id) {
      setNotifications([]);
      return;
    }

    let cancelled = false;
    let initialized = false;
    let knownIds = new Set<string>();
    const refresh = async () => {
      try {
        const { data } = await api.get<ApiNotification[]>('/api/notifications/me');
        if (cancelled) return;
        const mapped = data.map(mapNotification);
        if (initialized) {
          mapped.filter(value => !value.read && !knownIds.has(value.id))
            .forEach(value => toast(value.title, { description: value.description }));
        }
        knownIds = new Set(mapped.map(value => value.id));
        initialized = true;
        setNotifications(mapped);
      } catch {
        // Une indisponibilite temporaire du support ne doit pas bloquer toute l'application.
      }
    };

    void refresh();
    const interval = window.setInterval(refresh, 15_000);
    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
  }, [user?.id]);

  const markAsRead = (id: string) => {
    setNotifications(values => values.map(value => value.id === id ? { ...value, read: true } : value));
    void api.patch(`/api/notifications/${id}/read`);
  };

  const markAllAsRead = () => {
    setNotifications(values => values.map(value => ({ ...value, read: true })));
    void api.patch('/api/notifications/me/read-all');
  };

  const clearAll = () => {
    setNotifications([]);
    void api.delete('/api/notifications/me');
  };

  const unreadCount = notifications.filter(value => !value.read).length;

  return (
    <NotificationContext.Provider value={{ notifications, unreadCount, markAsRead, markAllAsRead, clearAll }}>
      {children}
    </NotificationContext.Provider>
  );
};

export const useNotifications = () => {
  const context = useContext(NotificationContext);
  if (!context) throw new Error('useNotifications must be used within NotificationProvider');
  return context;
};
