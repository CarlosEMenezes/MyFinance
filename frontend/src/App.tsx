import { Route, Routes } from 'react-router-dom';

import { PRIMARY_NAV, setupNavWithBadge } from './app/navItems';
import { useLogEntry } from './app/useLogEntry';
import { BottomTabBar } from './components/BottomTabBar';
import { LogEntryForm } from './components/LogEntryForm';
import { SidebarNav } from './components/SidebarNav';
import { AccountsPage } from './features/accounts';
import { CardsPage } from './features/cards';
import { CategoriesPage } from './features/categories';
import { EarningsPage } from './features/earnings';
import { ExpensesPage } from './features/expenses';
import { GoalsPage } from './features/goals';
import { NotificationsPage } from './features/notifications';
import { useNotifications } from './features/notifications/hooks';
import { OverviewPage } from './features/overview';
import { SettingsPage } from './features/settings';
import { today } from './lib/dates';
import './styles/tokens.css';
import './styles/app.css';

/**
 * The application shell: navigation, the log dialog and the routes.
 *
 * Both navigations are always in the tree; which one is visible is decided by
 * the 940px rule in `app.css`, so the breakpoint needs no JavaScript and no
 * component has to know how wide the window is.
 *
 * The unread count is read once here and handed to both navs, because BR-12
 * says the count drives the badge and two readings of it could disagree.
 */
export default function App() {
  const { unreadCount } = useNotifications();
  const log = useLogEntry();
  const setupNav = setupNavWithBadge(unreadCount);

  return (
    <div className="shell">
      <SidebarNav
        primary={PRIMARY_NAV}
        setup={setupNav}
        onLogEntry={log.openDialog}
        defaultCurrency={log.defaultCurrency}
        fxUpdatedAt={log.fxUpdatedAt}
      />

      <main className="main">
        <div className="mtop">
          <p className="brand">BUDGET TRACKER</p>
          <button type="button" className="btn btn-primary" onClick={log.openDialog}>
            + Log
          </button>
        </div>

        <Routes>
          <Route path="/" element={<OverviewPage />} />
          <Route path="/earnings" element={<EarningsPage />} />
          <Route path="/expenses" element={<ExpensesPage />} />
          <Route path="/goals" element={<GoalsPage />} />
          <Route path="/accounts" element={<AccountsPage />} />
          <Route path="/cards" element={<CardsPage />} />
          <Route path="/categories" element={<CategoriesPage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Routes>
      </main>

      <BottomTabBar items={PRIMARY_NAV} />

      <LogEntryForm
        open={log.open}
        onClose={log.closeDialog}
        onSubmit={log.submit}
        categories={log.categories}
        paymentMethods={log.paymentMethods}
        defaultCurrency={log.defaultCurrency}
        fx={log.fx}
        today={today()}
      />
    </div>
  );
}
