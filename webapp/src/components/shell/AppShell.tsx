import { Outlet } from 'react-router-dom';
import { TopBar } from './TopBar';

/**
 * AppShell — the stable layout every authenticated route renders inside.
 * Single `<header>` banner, single `<main>` region; route-level components
 * render into the <Outlet/>. Route transitions don't tear down the shell,
 * so the TopBar (and anything else living here) persists across navigation.
 */
export function AppShell() {
  return (
    <div className="flex min-h-full flex-col bg-background">
      <TopBar />
      <main id="main-content" className="flex-1 px-4 py-8 sm:px-6 lg:px-8" tabIndex={-1}>
        <div className="mx-auto w-full max-w-7xl">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
