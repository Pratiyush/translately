import { Monitor, Moon, Sun } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useTheme, type Theme } from '@/theme/ThemeProvider';

const order: Theme[] = ['light', 'dark', 'system'];
const labels: Record<Theme, string> = {
  light: 'Light theme',
  dark: 'Dark theme',
  system: 'System theme',
};

export function ThemeToggle() {
  const { theme, setTheme } = useTheme();
  const next = order[(order.indexOf(theme) + 1) % order.length]!;
  const Icon = theme === 'light' ? Sun : theme === 'dark' ? Moon : Monitor;
  return (
    <Button
      variant="ghost"
      size="icon"
      aria-label={`${labels[theme]} — click for ${labels[next]}`}
      onClick={() => setTheme(next)}
    >
      <Icon className="h-5 w-5" aria-hidden="true" />
    </Button>
  );
}
