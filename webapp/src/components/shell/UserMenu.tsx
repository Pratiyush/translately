import { LogOut, Settings, User } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { useAuth } from '@/lib/auth/useAuth';
import { t } from '@/i18n';

/**
 * Avatar-triggered user menu. The trigger itself is an icon-only button — it
 * carries an explicit `aria-label` so screen readers announce it. Menu items
 * use Radix semantics (role="menuitem"), so arrow keys + Esc behave without
 * extra wiring. "Sign out" clears the mock auth store and bounces the user
 * to the sign-in placeholder that lands in T117.
 */

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return (parts[0] ?? '').slice(0, 2).toUpperCase();
  return (parts[0]!.charAt(0) + parts[parts.length - 1]!.charAt(0)).toUpperCase();
}

export function UserMenu() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();

  if (!user) return null;

  const handleSignOut = () => {
    signOut();
    navigate('/signin');
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label={t('user.menu.open')}
          className="h-9 w-9 rounded-full p-0"
          data-testid="user-menu-trigger"
        >
          <Avatar className="h-8 w-8">
            {user.avatarUrl ? <AvatarImage src={user.avatarUrl} alt="" /> : null}
            <AvatarFallback>{initials(user.fullName)}</AvatarFallback>
          </Avatar>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="min-w-[14rem]">
        <DropdownMenuLabel>
          <div className="flex flex-col gap-0.5">
            <span className="text-sm font-semibold text-foreground">{user.fullName}</span>
            <span className="truncate text-xs font-normal text-muted-foreground">{user.email}</span>
          </div>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => navigate('/profile')} data-testid="user-menu-profile">
          <User className="h-4 w-4" aria-hidden="true" />
          <span>{t('user.menu.profile')}</span>
        </DropdownMenuItem>
        <DropdownMenuItem onSelect={() => navigate('/settings')} data-testid="user-menu-settings">
          <Settings className="h-4 w-4" aria-hidden="true" />
          <span>{t('user.menu.settings')}</span>
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={handleSignOut} data-testid="user-menu-sign-out">
          <LogOut className="h-4 w-4" aria-hidden="true" />
          <span>{t('user.menu.sign-out')}</span>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
