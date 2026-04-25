import { Header } from './Header';
import { Footer } from './Footer';

function Layout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex flex-col min-h-screen bg-background">
      <Header />
      <main style={{ flex: 1, width: '100%' }}>
        <div style={{ maxWidth: '80rem', margin: '0 auto', padding: '2rem 1.5rem' }}>
          {children}
        </div>
      </main>
      <Footer />
    </div>
  );
}

export { Layout };
