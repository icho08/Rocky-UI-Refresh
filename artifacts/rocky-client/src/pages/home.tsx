import { useState, useRef, useCallback, useMemo } from "react";
import { Search, X, GripHorizontal, Minus } from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";

type Module = {
  id: string;
  name: string;
  enabled: boolean;
};

type Category = {
  name: string;
  number: number;
  modules: Module[];
};

const INITIAL_CATEGORIES: Category[] = [
  {
    name: "AUTOMATION",
    number: 1,
    modules: [
      { id: "kill-aura", name: "Kill Aura", enabled: false },
      { id: "auto-clicker", name: "Auto Clicker", enabled: false },
      { id: "fast-use", name: "Fast Use", enabled: false },
      { id: "auto-tool", name: "Auto Tool", enabled: false },
      { id: "drag-click", name: "Drag Click", enabled: true },
    ],
  },
  {
    name: "MOVEMENT",
    number: 2,
    modules: [
      { id: "auto-xp", name: "Auto XP", enabled: false },
      { id: "bunny-hop", name: "Bunny Hop", enabled: false },
      { id: "quick-pearl", name: "Quick Pearl", enabled: true },
      { id: "fast-break", name: "Fast Break", enabled: false },
      { id: "freecam", name: "Freecam", enabled: true },
      { id: "auto-sprint", name: "Auto Sprint", enabled: false },
    ],
  },
  {
    name: "ESP",
    number: 2,
    modules: [
      { id: "fullbright", name: "Fullbright", enabled: true },
      { id: "no-view-bobbing", name: "No View Bobbing", enabled: true },
      { id: "player-esp", name: "Player ESP", enabled: false },
      { id: "storage-esp", name: "Storage ESP", enabled: false },
      { id: "chams", name: "Chams", enabled: false },
      { id: "health-display", name: "Health Display", enabled: false },
      { id: "armor-display", name: "Armor Display", enabled: false },
    ],
  },
  {
    name: "PVP",
    number: 4,
    modules: [
      { id: "aim-assist", name: "Aim Assist", enabled: false },
      { id: "silent-aim", name: "Silent Aim", enabled: true },
      { id: "reach", name: "Reach", enabled: false },
      { id: "hit-swap", name: "Hit Swap", enabled: true },
      { id: "trigger-bot", name: "Trigger Bot", enabled: true },
      { id: "auto-w-tap", name: "Auto W-Tap", enabled: false },
      { id: "no-miss-delay", name: "No Miss Delay", enabled: false },
      { id: "shield-breaker", name: "Shield Breaker", enabled: false },
      { id: "jump-reset", name: "Jump Reset", enabled: true },
      { id: "hitbox-expand", name: "Hitbox Expand", enabled: false },
      { id: "anti-knockback", name: "Anti Knockback", enabled: false },
    ],
  },
  {
    name: "BRIDGING",
    number: 1,
    modules: [
      { id: "bridge-assist", name: "Bridge Assist", enabled: false },
      { id: "god-bridge", name: "God Bridge", enabled: false },
      { id: "smart-bridge", name: "Smart Bridge", enabled: true },
    ],
  },
  {
    name: "NETWORK",
    number: 0,
    modules: [
      { id: "ping-spoof", name: "Ping Spoof", enabled: false },
      { id: "fake-lag", name: "Fake Lag", enabled: false },
      { id: "pack-spoof", name: "Pack Spoof", enabled: false },
      { id: "version-spoof", name: "Version Spoof", enabled: false },
    ],
  },
  {
    name: "GUI",
    number: 2,
    modules: [
      { id: "hud", name: "HUD", enabled: false },
      { id: "target-hud", name: "Target HUD", enabled: false },
      { id: "click-gui", name: "Click GUI", enabled: true },
      { id: "friends", name: "Friends", enabled: true },
      { id: "self-destruct", name: "Self Destruct", enabled: false },
    ],
  },
  {
    name: "INVENTORY",
    number: 0,
    modules: [
      { id: "double-hand", name: "Double Hand", enabled: false },
      { id: "auto-totem", name: "Auto Totem", enabled: false },
      { id: "auto-pot", name: "Auto Pot", enabled: false },
      { id: "pot-refill", name: "Pot Refill", enabled: false },
      { id: "totem-swap", name: "Totem Swap", enabled: false },
    ],
  },
  {
    name: "CRYSTAL",
    number: 1,
    modules: [
      { id: "anchor-aura", name: "Anchor Aura", enabled: false },
      { id: "auto-crystal", name: "Auto Crystal", enabled: false },
      { id: "crystal-aura", name: "Crystal Aura", enabled: false },
      { id: "crystal-optimizer", name: "Crystal Optimizer", enabled: false },
      { id: "double-anchor", name: "Double Anchor", enabled: false },
      { id: "hover-totem", name: "Hover Totem", enabled: false },
      { id: "auto-fireball", name: "Auto Fireball", enabled: true },
      { id: "auto-trap", name: "Auto Trap", enabled: false },
    ],
  },
];

const DEFAULT_POSITIONS: Record<string, { x: number; y: number }> = {
  AUTOMATION: { x: 20, y: 80 },
  MOVEMENT: { x: 200, y: 80 },
  ESP: { x: 380, y: 80 },
  PVP: { x: 560, y: 80 },
  BRIDGING: { x: 740, y: 80 },
  NETWORK: { x: 20, y: 360 },
  GUI: { x: 200, y: 360 },
  INVENTORY: { x: 380, y: 360 },
  CRYSTAL: { x: 620, y: 320 },
};

function Toggle({ enabled, onChange }: { enabled: boolean; onChange: () => void }) {
  return (
    <button
      onClick={(e) => { e.stopPropagation(); onChange(); }}
      className={`relative w-9 h-5 rounded-sm flex-shrink-0 transition-all duration-200 focus:outline-none ${
        enabled
          ? "bg-cyan-500/20 border border-cyan-500/60 shadow-[0_0_8px_rgba(34,211,238,0.35)]"
          : "bg-[#252525] border border-white/15"
      }`}
      style={{ borderRadius: "3px" }}
    >
      <motion.div
        className={`absolute top-[3px] w-[11px] h-[11px] rounded-sm transition-colors duration-200 ${
          enabled
            ? "bg-cyan-400 shadow-[0_0_6px_rgba(34,211,238,0.9)]"
            : "bg-[#555]"
        }`}
        style={{ borderRadius: "2px" }}
        animate={{ left: enabled ? "calc(100% - 14px)" : "3px" }}
        transition={{ type: "spring", stiffness: 600, damping: 35 }}
      />
    </button>
  );
}

function DraggablePanel({
  category,
  position,
  onPositionChange,
  onToggle,
  zIndex,
  onFocus,
  searchQuery,
}: {
  category: Category;
  position: { x: number; y: number };
  onPositionChange: (name: string, pos: { x: number; y: number }) => void;
  onToggle: (catName: string, moduleId: string) => void;
  zIndex: number;
  onFocus: (name: string) => void;
  searchQuery: string;
}) {
  const [minimized, setMinimized] = useState(false);
  const dragRef = useRef({ dragging: false, startX: 0, startY: 0, origX: 0, origY: 0 });

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    onFocus(category.name);
    dragRef.current = {
      dragging: true,
      startX: e.clientX,
      startY: e.clientY,
      origX: position.x,
      origY: position.y,
    };

    const onMove = (me: MouseEvent) => {
      if (!dragRef.current.dragging) return;
      onPositionChange(category.name, {
        x: dragRef.current.origX + me.clientX - dragRef.current.startX,
        y: dragRef.current.origY + me.clientY - dragRef.current.startY,
      });
    };
    const onUp = () => {
      dragRef.current.dragging = false;
      window.removeEventListener("mousemove", onMove);
      window.removeEventListener("mouseup", onUp);
    };
    window.addEventListener("mousemove", onMove);
    window.addEventListener("mouseup", onUp);
  }, [position, category.name, onPositionChange, onFocus]);

  const activeCount = category.modules.filter(m => m.enabled).length;

  const visibleModules = searchQuery
    ? category.modules.filter(m => m.name.toLowerCase().includes(searchQuery.toLowerCase()))
    : category.modules;

  if (searchQuery && visibleModules.length === 0) return null;

  return (
    <div
      className="absolute select-none"
      style={{ left: position.x, top: position.y, zIndex, width: 168 }}
      onMouseDown={() => onFocus(category.name)}
    >
      {/* Panel */}
      <div
        className="border border-white/[0.08] overflow-hidden"
        style={{
          background: "rgba(10,10,10,0.97)",
          boxShadow: "0 4px 24px rgba(0,0,0,0.6), inset 0 1px 0 rgba(255,255,255,0.04)",
        }}
      >
        {/* Header */}
        <div
          className="flex items-center gap-0 cursor-grab active:cursor-grabbing border-b border-white/[0.07]"
          style={{ background: "rgba(255,255,255,0.03)", userSelect: "none" }}
          onMouseDown={handleMouseDown}
        >
          <div
            className="flex items-center justify-center text-[10px] font-bold font-mono border-r border-white/[0.07] shrink-0"
            style={{
              width: 26,
              height: 28,
              color: activeCount > 0 ? "#22d3ee" : "rgba(255,255,255,0.3)",
              background: activeCount > 0 ? "rgba(34,211,238,0.08)" : "transparent",
            }}
          >
            {activeCount}
          </div>

          <span
            className="flex-1 text-[10px] font-mono tracking-[0.15em] px-2.5 py-1.5"
            style={{ color: "rgba(255,255,255,0.65)", letterSpacing: "0.12em" }}
          >
            {category.name}
          </span>

          <div className="flex items-center">
            <button
              className="flex items-center justify-center hover:bg-white/5 transition-colors"
              style={{ width: 26, height: 28 }}
              onClick={() => setMinimized(v => !v)}
            >
              <Minus className="w-3 h-3" style={{ color: "rgba(255,255,255,0.25)" }} />
            </button>
          </div>
        </div>

        {/* Module list */}
        <AnimatePresence initial={false}>
          {!minimized && (
            <motion.div
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: "auto", opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              transition={{ duration: 0.15 }}
              style={{ overflow: "hidden" }}
            >
              {visibleModules.map((module) => (
                <div
                  key={module.id}
                  className="flex items-center justify-between group cursor-pointer"
                  style={{
                    padding: "5px 8px 5px 10px",
                    borderBottom: "1px solid rgba(255,255,255,0.03)",
                    background: module.enabled
                      ? "rgba(34,211,238,0.05)"
                      : "transparent",
                    borderLeft: module.enabled
                      ? "2px solid rgba(34,211,238,0.7)"
                      : "2px solid transparent",
                    transition: "background 0.15s, border-color 0.15s",
                  }}
                  onClick={() => onToggle(category.name, module.id)}
                  onMouseEnter={e => {
                    if (!module.enabled) {
                      (e.currentTarget as HTMLDivElement).style.background = "rgba(255,255,255,0.03)";
                    }
                  }}
                  onMouseLeave={e => {
                    if (!module.enabled) {
                      (e.currentTarget as HTMLDivElement).style.background = "transparent";
                    }
                  }}
                >
                  <span
                    className="text-[11px] font-mono truncate flex-1 mr-2"
                    style={{
                      color: module.enabled ? "#22d3ee" : "rgba(255,255,255,0.55)",
                      fontWeight: module.enabled ? 500 : 400,
                      textShadow: module.enabled ? "0 0 8px rgba(34,211,238,0.5)" : "none",
                      transition: "color 0.15s",
                    }}
                  >
                    {module.name}
                  </span>
                  <Toggle
                    enabled={module.enabled}
                    onChange={() => onToggle(category.name, module.id)}
                  />
                </div>
              ))}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}

export default function Home() {
  const [categories, setCategories] = useState<Category[]>(INITIAL_CATEGORIES);
  const [positions, setPositions] = useState(DEFAULT_POSITIONS);
  const [zOrders, setZOrders] = useState<Record<string, number>>(() =>
    Object.fromEntries(INITIAL_CATEGORIES.map((c, i) => [c.name, i + 1]))
  );
  const [maxZ, setMaxZ] = useState(INITIAL_CATEGORIES.length + 1);
  const [search, setSearch] = useState("");
  const [selectedModule, setSelectedModule] = useState<{ cat: string; name: string; desc: string } | null>(null);

  const handleToggle = useCallback((catName: string, moduleId: string) => {
    setCategories(prev =>
      prev.map(cat =>
        cat.name !== catName ? cat : {
          ...cat,
          modules: cat.modules.map(m =>
            m.id !== moduleId ? m : { ...m, enabled: !m.enabled }
          ),
        }
      )
    );
  }, []);

  const handlePositionChange = useCallback((name: string, pos: { x: number; y: number }) => {
    setPositions(prev => ({ ...prev, [name]: pos }));
  }, []);

  const handleFocus = useCallback((name: string) => {
    setMaxZ(prev => {
      const next = prev + 1;
      setZOrders(z => ({ ...z, [name]: next }));
      return next;
    });
  }, []);

  const totalActive = useMemo(() =>
    categories.reduce((acc, cat) => acc + cat.modules.filter(m => m.enabled).length, 0),
    [categories]
  );

  const totalModules = useMemo(() =>
    categories.reduce((acc, cat) => acc + cat.modules.length, 0),
    [categories]
  );

  const activeModule = useMemo(() => {
    if (!selectedModule) return null;
    const cat = categories.find(c => c.name === selectedModule.cat);
    return cat?.modules.find(m => m.name === selectedModule.name) ?? null;
  }, [categories, selectedModule]);

  return (
    <div
      className="w-full h-screen overflow-hidden relative font-mono"
      style={{ background: "#080808", userSelect: "none" }}
    >
      {/* Subtle background glow */}
      <div
        className="absolute pointer-events-none"
        style={{
          top: "-20%", left: "-10%", width: "55%", height: "55%",
          background: "radial-gradient(ellipse, rgba(34,211,238,0.04) 0%, transparent 70%)",
        }}
      />

      {/* Top bar */}
      <div
        className="absolute top-0 left-0 right-0 flex items-center z-50 border-b"
        style={{
          height: 40,
          background: "rgba(6,6,6,0.98)",
          borderColor: "rgba(255,255,255,0.07)",
          backdropFilter: "blur(10px)",
        }}
      >
        {/* Logo */}
        <div className="px-5 border-r" style={{ borderColor: "rgba(255,255,255,0.07)", height: "100%", display: "flex", alignItems: "center" }}>
          <span
            className="text-sm font-bold tracking-[0.25em]"
            style={{ color: "#22d3ee", textShadow: "0 0 12px rgba(34,211,238,0.5)" }}
          >
            ROCKY
          </span>
        </div>

        {/* Selected module breadcrumb */}
        <div className="px-5 flex items-center gap-2 flex-1">
          {selectedModule ? (
            <>
              <span className="text-[11px]" style={{ color: "#22d3ee" }}>{selectedModule.name}</span>
              <span className="text-[11px]" style={{ color: "rgba(255,255,255,0.2)" }}>—</span>
              <span className="text-[11px]" style={{ color: "rgba(255,255,255,0.35)" }}>{selectedModule.desc}</span>
            </>
          ) : (
            <span className="text-[11px]" style={{ color: "rgba(255,255,255,0.2)" }}>
              Select a module to view details
            </span>
          )}
        </div>

        {/* Search */}
        <div className="relative mx-4">
          <Search
            className="absolute left-3 top-1/2 -translate-y-1/2 w-3 h-3 pointer-events-none"
            style={{ color: search ? "#22d3ee" : "rgba(255,255,255,0.3)" }}
          />
          <input
            type="text"
            placeholder="Search modules..."
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="text-[11px] font-mono pl-8 pr-3 focus:outline-none transition-all"
            style={{
              background: "rgba(255,255,255,0.04)",
              border: `1px solid ${search ? "rgba(34,211,238,0.5)" : "rgba(255,255,255,0.1)"}`,
              color: "rgba(255,255,255,0.75)",
              width: 180,
              height: 26,
              boxShadow: search ? "0 0 10px rgba(34,211,238,0.1)" : "none",
            }}
          />
          {search && (
            <button
              className="absolute right-2 top-1/2 -translate-y-1/2"
              onClick={() => setSearch("")}
            >
              <X className="w-3 h-3" style={{ color: "rgba(255,255,255,0.4)" }} />
            </button>
          )}
        </div>

        {/* Active count */}
        <div
          className="px-5 border-l flex items-center gap-2"
          style={{ borderColor: "rgba(255,255,255,0.07)", height: "100%" }}
        >
          <span className="text-[11px]" style={{ color: "rgba(255,255,255,0.3)" }}>
            {totalActive} active
          </span>
          <span className="text-[11px]" style={{ color: "rgba(255,255,255,0.15)" }}>/</span>
          <span className="text-[11px]" style={{ color: "rgba(255,255,255,0.2)" }}>
            {totalModules} total
          </span>
        </div>
      </div>

      {/* Draggable panels */}
      <div className="absolute inset-0 pt-10">
        {categories.map(cat => (
          <DraggablePanel
            key={cat.name}
            category={cat}
            position={positions[cat.name] ?? { x: 20, y: 80 }}
            onPositionChange={handlePositionChange}
            onToggle={handleToggle}
            zIndex={zOrders[cat.name] ?? 1}
            onFocus={handleFocus}
            searchQuery={search}
          />
        ))}
      </div>

      {/* Hint */}
      <div
        className="absolute bottom-4 right-5 text-[10px] pointer-events-none"
        style={{ color: "rgba(255,255,255,0.12)", letterSpacing: "0.08em" }}
      >
        DRAG HEADERS TO MOVE · CLICK MODULE TO TOGGLE
      </div>
    </div>
  );
}
