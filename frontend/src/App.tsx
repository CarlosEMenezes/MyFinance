import './styles/tokens.css';
import './styles/app.css';

const BRAND = 'BUDGET TRACKER';

/**
 * The application shell.
 *
 * Foundation only: it establishes the ground, the type pairing and the
 * responsive frame from the design. The navigation, page header controls and
 * the nine pages arrive as their own components, each built and tested in its
 * own folder before any page consumes it (spec §0.6, §6).
 */
export default function App() {
  return (
    <div className="shell">
      <aside className="side">
        <div className="brand">{BRAND}</div>
        <div className="brand-kicker">Fig. 01 — Planning</div>
      </aside>

      <main className="main">
        <div className="mtop">
          <div className="brand">{BRAND}</div>
        </div>

        <header className="page-header">
          <div>
            <div className="page-kicker">Foundation</div>
            <h1 className="page-title">Budget Tracker</h1>
          </div>
        </header>
      </main>
    </div>
  );
}
