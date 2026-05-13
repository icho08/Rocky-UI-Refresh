import { useState, useMemo } from "react";
import { Search, Monitor, Swords, Move, Eye, Network, Package, Target, Settings, Info } from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";
import { Tooltip, TooltipContent, TooltipTrigger, TooltipProvider } from "@/components/ui/tooltip";

type Module = {
  id: string;
  name: string;
  category: string;
  enabled: boolean;
  description?: string;
};

const INITIAL_MODULES: Module[] = [
  // Automation
  { id: "kill-aura", name: "Kill Aura", category: "Automation", enabled: false },
  { id: "auto-clicker", name: "Auto Clicker", category: "Automation", enabled: false },
  { id: "drag-click", name: "Drag Click", category: "Automation", enabled: false },
  // Movement
  { id: "auto-xp", name: "Auto XP", category: "Movement", enabled: false },
  { id: "bunny-hop", name: "Bunny Hop", category: "Movement", enabled: true },
  { id: "quick-pearl", name: "Quick Pearl", category: "Movement", enabled: false },
  { id: "fast-break", name: "Fast Break", category: "Movement", enabled: true },
  { id: "freecam", name: "Freecam", category: "Movement", enabled: false },
  { id: "auto-sprint", name: "Auto Sprint", category: "Movement", enabled: true },
  // ESP
  { id: "fullbright", name: "Fullbright", category: "ESP", enabled: false },
  { id: "no-view-bobbing", name: "No View Bobbing", category: "ESP", enabled: false },
  { id: "player-esp", name: "Player ESP", category: "ESP", enabled: false },
  { id: "storage-esp", name: "Storage ESP", category: "ESP", enabled: false },
  { id: "chams", name: "Chams", category: "ESP", enabled: true },
  { id: "health-display", name: "Health Display", category: "ESP", enabled: false },
  { id: "armor-display", name: "Armor Display", category: "ESP", enabled: false },
  // PvP
  { id: "aim-assist", name: "Aim Assist", category: "PvP", enabled: false },
  { id: "silent-aim", name: "Silent Aim", category: "PvP", enabled: false },
  { id: "reach", name: "Reach", category: "PvP", enabled: false },
  { id: "hit-swap", name: "Hit Swap", category: "PvP", enabled: false },
  { id: "trigger-bot", name: "Trigger Bot", category: "PvP", enabled: true },
  { id: "auto-w-tap", name: "Auto W-Tap", category: "PvP", enabled: false },
  { id: "no-miss-delay", name: "No Miss Delay", category: "PvP", enabled: false },
  { id: "shield-breaker", name: "Shield Breaker", category: "PvP", enabled: false },
  { id: "jump-reset", name: "Jump Reset", category: "PvP", enabled: false },
  { id: "hitbox-expand", name: "Hitbox Expand", category: "PvP", enabled: false },
  { id: "anti-knockback", name: "Anti Knockback", category: "PvP", enabled: false },
  // Bridging
  { id: "bridge-assist", name: "Bridge Assist", category: "Bridging", enabled: false },
  { id: "god-bridge", name: "God Bridge", category: "Bridging", enabled: false },
  { id: "smart-bridge", name: "Smart Bridge", category: "Bridging", enabled: false },
  // Network
  { id: "ping-spoof", name: "Ping Spoof", category: "Network", enabled: false },
  { id: "fake-lag", name: "Fake Lag", category: "Network", enabled: false },
  { id: "pack-spoof", name: "Pack Spoof", category: "Network", enabled: false },
  { id: "version-spoof", name: "Version Spoof", category: "Network", enabled: false },
  // GUI
  { id: "hud", name: "HUD", category: "GUI", enabled: false },
  { id: "target-hud", name: "Target HUD", category: "GUI", enabled: false },
  { id: "click-gui", name: "Click GUI", category: "GUI", enabled: true },
  { id: "friends", name: "Friends", category: "GUI", enabled: false },
  { id: "self-destruct", name: "Self Destruct", category: "GUI", enabled: false },
  // Inventory
  { id: "double-hand", name: "Double Hand", category: "Inventory", enabled: false },
  { id: "auto-totem", name: "Auto Totem", category: "Inventory", enabled: false },
  { id: "auto-pot", name: "Auto Pot", category: "Inventory", enabled: false },
  { id: "pot-refill", name: "Pot Refill", category: "Inventory", enabled: false },
  { id: "totem-swap", name: "Totem Swap", category: "Inventory", enabled: false },
  // Crystal
  { id: "anchor-aura", name: "Anchor Aura", category: "Crystal", enabled: false },
  { id: "auto-crystal", name: "Auto Crystal", category: "Crystal", enabled: false },
  { id: "crystal-aura", name: "Crystal Aura", category: "Crystal", enabled: false },
  { id: "crystal-optimizer", name: "Crystal Optimizer", category: "Crystal", enabled: false, description: "Optimizes crystal PvP crystals" },
  { id: "double-anchor", name: "Double Anchor", category: "Crystal", enabled: false },
  { id: "hover-totem", name: "Hover Totem", category: "Crystal", enabled: false },
  { id: "auto-fireball", name: "Auto Fireball", category: "Crystal", enabled: false },
  { id: "auto-trap", name: "Auto Trap", category: "Crystal", enabled: false },
];

const CATEGORIES = [
  { name: "Automation", icon: Settings },
  { name: "Movement", icon: Move },
  { name: "ESP", icon: Eye },
  { name: "PvP", icon: Swords },
  { name: "Bridging", icon: Monitor },
  { name: "Network", icon: Network },
  { name: "GUI", icon: Target },
  { name: "Inventory", icon: Package },
  { name: "Crystal", icon: Settings },
];

export default function Home() {
  const [modules, setModules] = useState<Module[]>(INITIAL_MODULES);
  const [activeCategory, setActiveCategory] = useState(CATEGORIES[0].name);
  const [search, setSearch] = useState("");

  const toggleModule = (id: string) => {
    setModules(modules.map(m => m.id === id ? { ...m, enabled: !m.enabled } : m));
  };

  const filteredModules = useMemo(() => {
    if (search) {
      return modules.filter(m => m.name.toLowerCase().includes(search.toLowerCase()));
    }
    return modules.filter(m => m.category === activeCategory);
  }, [modules, activeCategory, search]);

  const activeCount = modules.filter(m => m.enabled).length;

  return (
    <div className="min-h-screen w-full bg-[#050505] text-white flex overflow-hidden selection:bg-cyan-500/30 font-sans relative">
      {/* Background ambient light */}
      <div className="absolute top-[-20%] left-[-10%] w-[50%] h-[50%] bg-cyan-900/10 blur-[150px] pointer-events-none rounded-full" />
      <div className="absolute bottom-[-20%] right-[-10%] w-[50%] h-[50%] bg-cyan-900/10 blur-[150px] pointer-events-none rounded-full" />

      {/* Sidebar */}
      <div className="w-64 border-r border-white/5 bg-[#0a0a0a]/80 backdrop-blur-xl flex flex-col z-10 relative">
        <div className="p-6 border-b border-white/5">
          <h1 className="text-4xl font-bold tracking-widest text-transparent bg-clip-text bg-gradient-to-r from-cyan-400 to-cyan-600 mb-1" style={{ textShadow: '0 0 20px rgba(34,211,238,0.3)' }}>
            ROCKY
          </h1>
          <p className="text-xs text-cyan-500/50 uppercase tracking-[0.2em] font-mono">Utility Client v2.0</p>
        </div>

        <div className="flex-1 overflow-y-auto py-4 px-3 space-y-1">
          {CATEGORIES.map((cat) => {
            const Icon = cat.icon;
            const isActive = !search && activeCategory === cat.name;
            return (
              <button
                key={cat.name}
                onClick={() => {
                  setActiveCategory(cat.name);
                  setSearch("");
                }}
                className={`w-full flex items-center gap-3 px-4 py-3 text-left transition-all duration-200 relative group overflow-hidden ${
                  isActive ? "text-cyan-400" : "text-white/40 hover:text-white/80"
                }`}
              >
                {isActive && (
                  <motion.div 
                    layoutId="activeTab"
                    className="absolute inset-0 bg-gradient-to-r from-cyan-500/10 to-transparent border-l-2 border-cyan-400"
                  />
                )}
                <Icon className={`w-4 h-4 z-10 ${isActive ? 'drop-shadow-[0_0_8px_rgba(34,211,238,0.8)]' : ''}`} />
                <span className="font-medium tracking-wider text-sm z-10">{cat.name.toUpperCase()}</span>
                
                {/* Active module count per category */}
                {modules.filter(m => m.category === cat.name && m.enabled).length > 0 && (
                  <span className={`ml-auto text-xs font-mono z-10 ${isActive ? 'text-cyan-400' : 'text-white/40'}`}>
                    {modules.filter(m => m.category === cat.name && m.enabled).length}
                  </span>
                )}
              </button>
            );
          })}
        </div>
        
        <div className="p-4 border-t border-white/5 bg-black/20">
          <div className="flex items-center justify-between text-xs font-mono text-white/40 mb-2">
            <span>ACTIVE MODULES</span>
            <span className="text-cyan-400">{activeCount} / {modules.length}</span>
          </div>
          <div className="h-1 bg-white/5 w-full overflow-hidden">
            <motion.div 
              className="h-full bg-cyan-500 shadow-[0_0_10px_rgba(34,211,238,0.8)]"
              initial={{ width: 0 }}
              animate={{ width: `${(activeCount / modules.length) * 100}%` }}
              transition={{ duration: 0.3 }}
            />
          </div>
        </div>
      </div>

      {/* Main Content */}
      <div className="flex-1 flex flex-col z-10 relative">
        <div className="h-20 border-b border-white/5 bg-[#0a0a0a]/50 backdrop-blur-md flex items-center px-8 justify-between">
          <div className="flex items-center gap-3 text-white/50">
            <h2 className="text-xl font-semibold tracking-wider text-white">
              {search ? 'SEARCH RESULTS' : activeCategory.toUpperCase()}
            </h2>
            <span className="text-xs font-mono px-2 py-1 bg-white/5 text-white/40 border border-white/10">
              {filteredModules.length} MODULES
            </span>
          </div>

          <div className="relative w-64 group">
            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
              <Search className="h-4 w-4 text-white/30 group-focus-within:text-cyan-400 transition-colors" />
            </div>
            <input
              type="text"
              placeholder="SEARCH MODULES..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full bg-[#111] border border-white/10 text-white pl-10 pr-4 py-2 text-sm font-mono focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/50 transition-all placeholder:text-white/20"
            />
            {search && (
              <div className="absolute inset-0 border border-cyan-500/30 pointer-events-none animate-pulse" />
            )}
          </div>
        </div>

        <div className="flex-1 overflow-y-auto p-8">
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            <AnimatePresence>
              {filteredModules.map((module) => (
                <motion.div
                  layout
                  initial={{ opacity: 0, scale: 0.95 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.95 }}
                  transition={{ duration: 0.15 }}
                  key={module.id}
                >
                  <TooltipProvider delayDuration={0}>
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <button
                          onClick={() => toggleModule(module.id)}
                          className={`w-full group relative text-left p-4 border transition-all duration-300 overflow-hidden ${
                            module.enabled 
                              ? "bg-cyan-950/20 border-cyan-500/50 shadow-[0_0_15px_rgba(34,211,238,0.15)]" 
                              : "bg-[#0f0f0f] border-white/5 hover:border-white/20 hover:bg-[#151515]"
                          }`}
                        >
                          {/* Top-right corner cut effect (visual only) */}
                          <div className={`absolute top-0 right-0 w-4 h-4 border-t border-r transition-colors ${module.enabled ? 'border-cyan-400' : 'border-white/10 group-hover:border-white/30'}`} />
                          
                          {/* Active Scanline effect */}
                          {module.enabled && (
                            <motion.div 
                              className="absolute inset-0 bg-gradient-to-b from-transparent via-cyan-400/5 to-transparent h-[200%]"
                              animate={{ top: ['-100%', '100%'] }}
                              transition={{ duration: 2, repeat: Infinity, ease: "linear" }}
                            />
                          )}

                          <div className="flex justify-between items-start relative z-10">
                            <div>
                              <div className="flex items-center gap-2 mb-1">
                                <h3 className={`font-semibold tracking-wide ${module.enabled ? 'text-cyan-400 drop-shadow-[0_0_5px_rgba(34,211,238,0.8)]' : 'text-white/70'}`}>
                                  {module.name}
                                </h3>
                                {module.description && (
                                  <Info className="w-3 h-3 text-white/20" />
                                )}
                              </div>
                              {search && (
                                <p className="text-[10px] text-white/30 font-mono tracking-widest uppercase">
                                  {module.category}
                                </p>
                              )}
                            </div>

                            <div className="flex items-center">
                              <div className={`w-8 h-4 rounded-full border p-[1px] flex items-center transition-colors ${
                                module.enabled ? 'border-cyan-500/50 bg-cyan-900/30' : 'border-white/10 bg-black/50'
                              }`}>
                                <motion.div 
                                  className={`w-2.5 h-2.5 rounded-full ${module.enabled ? 'bg-cyan-400 shadow-[0_0_5px_rgba(34,211,238,1)]' : 'bg-white/20'}`}
                                  animate={{ x: module.enabled ? 16 : 0 }}
                                  transition={{ type: "spring", stiffness: 500, damping: 30 }}
                                />
                              </div>
                            </div>
                          </div>
                        </button>
                      </TooltipTrigger>
                      {module.description && (
                        <TooltipContent side="bottom" align="start" className="bg-[#111] border-white/10 text-white/70 font-mono text-xs p-2 rounded-none rounded-br-lg border-l-2 border-l-cyan-500">
                          {module.description}
                        </TooltipContent>
                      )}
                    </Tooltip>
                  </TooltipProvider>
                </motion.div>
              ))}
            </AnimatePresence>
            
            {filteredModules.length === 0 && (
              <div className="col-span-full py-20 flex flex-col items-center justify-center text-white/20">
                <Target className="w-12 h-12 mb-4 opacity-20" />
                <p className="font-mono tracking-widest">NO MODULES FOUND</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}