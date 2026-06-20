import { Layout } from './components/Layout';
import { SearchBox } from './components/SearchBox';
import { TrendingSearches } from './components/TrendingSearches';
import { SystemMetrics } from './components/SystemMetrics';

function App() {
  return (
    <Layout>
      <div className="w-full mb-8 relative z-50">
        <SearchBox />
      </div>
      
      <div className="w-full flex flex-col md:flex-row gap-6 relative z-10">
        <TrendingSearches />
        <SystemMetrics />
      </div>
    </Layout>
  );
}

export default App;
