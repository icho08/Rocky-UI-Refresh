import { useState, useMemo } from "react";
import { Search, Monitor, Swords, Move, Eye, Network, Package, Target, Settings, Zap, X, ChevronDown } from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";

type SettingType = "boolean" | "slider" | "mode" | "minmax";

type Setting = {
  id: string;
  label: string;
  description?: string;
  type: SettingType;
  value: boolean | number | string;
  min?: number;
  max?: number;
  step?: number;
  options?: string[];
  minValue?: number;
  maxValue?: number;
};

type Module = {
  id: string;
  name: string;
  category: string;
  enabled: boolean;
  description?: string;
  settings?: Setting[];
};

const INITIAL_MODULES: Module[] = [
  // Automation
  {
    id: "kill-aura", name: "Kill Aura", category: "Automation", enabled: false,
    description: "Automatically attacks nearby players",
    settings: [
      { id: "range", label: "Range", type: "slider", value: 3.5, min: 1, max: 6, step: 0.1 },
      { id: "cps", label: "CPS", type: "slider", value: 12, min: 1, max: 20, step: 1 },
      { id: "mode", label: "Mode", type: "mode", value: "Switch", options: ["Switch", "Single", "Multi"] },
      { id: "sort-targets", label: "Sort Targets", type: "boolean", value: true },
      { id: "rotate", label: "Rotate", type: "boolean", value: true },
      { id: "through-walls", label: "Through Walls", type: "boolean", value: false },
      { id: "only-sprint", label: "Only Sprint", type: "boolean", value: false },
    ],
  },
  {
    id: "auto-clicker", name: "Auto Clicker", category: "Automation", enabled: false,
    description: "Automatically clicks",
    settings: [
      { id: "mode", label: "Actions", type: "mode", value: "All", options: ["All", "Left", "Right"] },
      { id: "delay", label: "Delay (ms)", type: "slider", value: 0, min: 0, max: 1000, step: 1 },
      { id: "chance", label: "Chance %", type: "slider", value: 100, min: 0, max: 100, step: 1 },
      { id: "only-weapon", label: "Only Weapon", type: "boolean", value: true, description: "Only left clicks with weapon in hand" },
      { id: "only-blocks", label: "Only Blocks", type: "boolean", value: true, description: "Only right clicks blocks" },
      { id: "on-click", label: "On Click", type: "boolean", value: true },
    ],
  },
  {
    id: "drag-click", name: "Drag Click", category: "Automation", enabled: false,
    description: "Simulates drag clicking for high CPS",
    settings: [
      { id: "min-cps", label: "Min CPS", type: "slider", value: 14, min: 1, max: 40, step: 1 },
      { id: "max-cps", label: "Max CPS", type: "slider", value: 22, min: 1, max: 40, step: 1 },
    ],
  },
  // Movement
  {
    id: "auto-xp", name: "Auto XP", category: "Movement", enabled: false,
    description: "Automatically throws and picks up XP bottles",
  },
  {
    id: "bunny-hop", name: "Bunny Hop", category: "Movement", enabled: true,
    description: "Automatically jumps to maintain speed",
    settings: [
      { id: "mode", label: "Mode", type: "mode", value: "Vanilla", options: ["Vanilla", "Hypixel", "Legit"] },
      { id: "only-sprint", label: "Only While Sprint", type: "boolean", value: true },
    ],
  },
  {
    id: "quick-pearl", name: "Quick Pearl", category: "Movement", enabled: false,
    description: "Instantly throws ender pearl on keybind",
    settings: [
      { id: "bind", label: "Bind Key", type: "mode", value: "G", options: ["G", "H", "J", "K", "Mouse4", "Mouse5"] },
    ],
  },
  {
    id: "fast-break", name: "Fast Break", category: "Movement", enabled: true,
    description: "Removes block break delay",
    settings: [
      { id: "delay", label: "Delay (ticks)", type: "slider", value: 0, min: 0, max: 10, step: 1 },
    ],
  },
  {
    id: "freecam", name: "Freecam", category: "Movement", enabled: false,
    description: "Detaches camera from player body",
    settings: [
      { id: "speed", label: "Speed", type: "slider", value: 1.0, min: 0.1, max: 5.0, step: 0.1 },
    ],
  },
  {
    id: "auto-sprint", name: "Auto Sprint", category: "Movement", enabled: true,
    description: "Automatically sprints",
    settings: [
      { id: "direction", label: "Direction", type: "mode", value: "Forward", options: ["Forward", "Omni"], description: "Forward = vanilla-like, Omni = sprint in any direction" },
      { id: "keep-in-air", label: "Keep In Air", type: "boolean", value: true, description: "Maintain sprint state while jumping/falling" },
      { id: "keep-on-sneak", label: "Keep On Sneak", type: "boolean", value: false, description: "Don't release sprint when sneaking" },
      { id: "stop-on-hurt", label: "Release On Hurt", type: "boolean", value: true, description: "Briefly stop sprinting when taking damage" },
      { id: "hurt-release", label: "Hurt Release Ticks", type: "slider", value: 5, min: 1, max: 20, step: 1 },
    ],
  },
  // ESP
  {
    id: "fullbright", name: "Fullbright", category: "ESP", enabled: false,
    description: "Makes the world fully bright (Night Vision)",
  },
  {
    id: "no-view-bobbing", name: "No View Bobbing", category: "ESP", enabled: false,
    description: "Disables camera bobbing animation",
  },
  {
    id: "player-esp", name: "Player ESP", category: "ESP", enabled: false,
    description: "Draws boxes around players through walls",
    settings: [
      { id: "mode", label: "Mode", type: "mode", value: "Box", options: ["Box", "Outline", "Corner"] },
      { id: "tracers", label: "Tracers", type: "boolean", value: false },
      { id: "color-health", label: "Health Color", type: "boolean", value: true },
    ],
  },
  {
    id: "storage-esp", name: "Storage ESP", category: "ESP", enabled: false,
    description: "Highlights chests and containers through walls",
    settings: [
      { id: "range", label: "Range", type: "slider", value: 20, min: 5, max: 64, step: 1 },
      { id: "fill", label: "Fill", type: "boolean", value: true },
    ],
  },
  {
    id: "chams", name: "Chams", category: "ESP", enabled: true,
    description: "Renders players through walls with flat color",
    settings: [
      { id: "mode", label: "Mode", type: "mode", value: "Flat", options: ["Flat", "Textured", "Wireframe"] },
      { id: "behind", label: "Through Walls", type: "boolean", value: true },
    ],
  },
  {
    id: "health-display", name: "Health Display", category: "ESP", enabled: false,
    description: "Shows player health above their heads",
  },
  {
    id: "armor-display", name: "Armor Display", category: "ESP", enabled: false,
    description: "Shows armor durability on nearby players",
  },
  // PvP
  {
    id: "aim-assist", name: "Aim Assist", category: "PvP", enabled: false,
    description: "Smoothly aims at nearby players",
    settings: [
      { id: "range", label: "Range", type: "slider", value: 4.5, min: 1, max: 10, step: 0.1 },
      { id: "fov", label: "FOV", type: "slider", value: 60, min: 5, max: 180, step: 1 },
      { id: "aim-at", label: "Aim At", type: "mode", value: "Head", options: ["Head", "Chest", "Legs"] },
      { id: "speed-min", label: "Speed Min", type: "slider", value: 1.5, min: 0.1, max: 10, step: 0.1 },
      { id: "speed-max", label: "Speed Max", type: "slider", value: 3.5, min: 0.1, max: 10, step: 0.1 },
      { id: "acceleration", label: "Acceleration", type: "slider", value: 1.0, min: 0.1, max: 2.0, step: 0.1 },
      { id: "only-weapon", label: "Only Weapon", type: "boolean", value: true },
      { id: "on-left-click", label: "On Left Click", type: "boolean", value: true },
      { id: "stop-at-target", label: "Stop at Target", type: "boolean", value: true },
      { id: "jitter", label: "Jitter", type: "boolean", value: false },
      { id: "jitter-amount", label: "Jitter Amount", type: "slider", value: 0.5, min: 0.1, max: 2.0, step: 0.1 },
    ],
  },
  {
    id: "silent-aim", name: "Silent Aim", category: "PvP", enabled: false,
    description: "Aims at players without moving your camera",
    settings: [
      { id: "range", label: "Range", type: "slider", value: 4.0, min: 1, max: 6, step: 0.1 },
      { id: "fov", label: "FOV", type: "slider", value: 90, min: 5, max: 180, step: 1 },
    ],
  },
  {
    id: "reach", name: "Reach", category: "PvP", enabled: false,
    description: "Extends attack reach distance",
    settings: [
      { id: "distance", label: "Distance", type: "slider", value: 3.3, min: 3.0, max: 4.5, step: 0.01 },
      { id: "legit", label: "Legit", type: "boolean", value: true, description: "Varies the reach distance to bypass anti-cheats" },
      { id: "randomization", label: "Randomization", type: "slider", value: 0.05, min: 0.0, max: 0.2, step: 0.01 },
    ],
  },
  {
    id: "hit-swap", name: "Hit Swap", category: "PvP", enabled: false,
    description: "Swaps to sword on hit, then back",
    settings: [
      { id: "delay", label: "Swap Delay (ms)", type: "slider", value: 50, min: 0, max: 200, step: 5 },
    ],
  },
  {
    id: "trigger-bot", name: "Trigger Bot", category: "PvP", enabled: true,
    description: "Automatically attacks on crosshair",
    settings: [
      { id: "delay-mode", label: "Delay Mode", type: "mode", value: "Auto", options: ["Auto", "Manual"], description: "Auto uses game attack cooldown (recommended for 1.9+)" },
      { id: "sword-delay-min", label: "Sword Delay Min", type: "slider", value: 540, min: 0, max: 1000, step: 1 },
      { id: "sword-delay-max", label: "Sword Delay Max", type: "slider", value: 550, min: 0, max: 1000, step: 1 },
      { id: "axe-delay-min", label: "Axe Delay Min", type: "slider", value: 780, min: 0, max: 1000, step: 1 },
      { id: "axe-delay-max", label: "Axe Delay Max", type: "slider", value: 800, min: 0, max: 1000, step: 1 },
      { id: "max-reach", label: "Max Reach", type: "slider", value: 3.0, min: 2.5, max: 6.0, step: 0.1 },
      { id: "miss-chance", label: "Miss Chance %", type: "slider", value: 0, min: 0, max: 30, step: 1 },
      { id: "target-switch-delay", label: "Target Switch Delay", type: "slider", value: 80, min: 0, max: 500, step: 5 },
      { id: "weapon-only", label: "Weapon Only", type: "boolean", value: true, description: "Only attacks if holding a weapon" },
      { id: "check-shield", label: "Check Shield", type: "boolean", value: true },
      { id: "swing", label: "Swing Hand", type: "boolean", value: true },
      { id: "aim-jitter", label: "Aim Jitter", type: "boolean", value: true },
      { id: "respect-hurt-time", label: "Respect Hurt Time", type: "boolean", value: true },
      { id: "on-left-click", label: "On Left Click", type: "boolean", value: false },
      { id: "ignore-npcs", label: "Ignore NPCs", type: "boolean", value: true, description: "Prevents attacking fake players/bots" },
      { id: "same-player", label: "Same Player", type: "boolean", value: false, description: "Only hits the player you are currently attacking" },
    ],
  },
  {
    id: "auto-w-tap", name: "Auto W-Tap", category: "PvP", enabled: false,
    description: "Automatically releases W key on attack to W-tap",
    settings: [
      { id: "delay", label: "Release Ticks", type: "slider", value: 1, min: 1, max: 5, step: 1 },
    ],
  },
  {
    id: "no-miss-delay", name: "No Miss Delay", category: "PvP", enabled: false,
    description: "Removes attack penalty when missing",
  },
  {
    id: "shield-breaker", name: "Shield Breaker", category: "PvP", enabled: false,
    description: "Disables enemy shields by switching to axe",
  },
  {
    id: "jump-reset", name: "Jump Reset", category: "PvP", enabled: false,
    description: "Automatically jumps to reset hit cooldown",
    settings: [
      { id: "mode", label: "Mode", type: "mode", value: "Auto", options: ["Auto", "Manual"] },
    ],
  },
  {
    id: "hitbox-expand", name: "Hitbox Expand", category: "PvP", enabled: false,
    description: "Expands enemy hitboxes client-side",
    settings: [
      { id: "size", label: "Expand Size", type: "slider", value: 0.1, min: 0.0, max: 0.5, step: 0.01 },
    ],
  },
  {
    id: "anti-knockback", name: "Anti Knockback", category: "PvP", enabled: false,
    description: "Reduces or cancels knockback from attacks",
    settings: [
      { id: "horizontal", label: "Horizontal %", type: "slider", value: 100, min: 0, max: 100, step: 1 },
      { id: "vertical", label: "Vertical %", type: "slider", value: 100, min: 0, max: 100, step: 1 },
    ],
  },
  // Bridging
  {
    id: "bridge-assist", name: "Bridge Assist", category: "Bridging", enabled: false,
    description: "Assists with placing blocks while bridging",
    settings: [
      { id: "safe-walk", label: "Safe Walk", type: "boolean", value: true },
    ],
  },
  {
    id: "god-bridge", name: "God Bridge", category: "Bridging", enabled: false,
    description: "Automated god bridging",
  },
  {
    id: "smart-bridge", name: "Smart Bridge", category: "Bridging", enabled: false,
    description: "Intelligent auto-bridging with path detection",
    settings: [
      { id: "speed", label: "Speed", type: "slider", value: 1.0, min: 0.5, max: 2.0, step: 0.1 },
    ],
  },
  // Network
  {
    id: "ping-spoof", name: "Ping Spoof", category: "Network", enabled: false,
    description: "Spoofs your ping display in the tab menu",
    settings: [
      { id: "ping", label: "Fake Ping (ms)", type: "slider", value: 45, min: 1, max: 999, step: 1 },
    ],
  },
  {
    id: "fake-lag", name: "Fake Lag", category: "Network", enabled: false,
    description: "Holds packets to simulate lag on demand",
    settings: [
      { id: "delay", label: "Lag Amount (ms)", type: "slider", value: 200, min: 50, max: 2000, step: 10 },
      { id: "mode", label: "Mode", type: "mode", value: "Hold", options: ["Hold", "Toggle", "Burst"] },
    ],
  },
  {
    id: "pack-spoof", name: "Pack Spoof", category: "Network", enabled: false,
    description: "Spoofs your resource pack hash",
  },
  {
    id: "version-spoof", name: "Version Spoof", category: "Network", enabled: false,
    description: "Spoofs your Minecraft version string",
    settings: [
      { id: "version", label: "Version", type: "mode", value: "1.20.1", options: ["1.8.9", "1.12.2", "1.16.5", "1.18.2", "1.19.4", "1.20.1", "1.21"] },
    ],
  },
  // GUI
  {
    id: "hud", name: "HUD", category: "GUI", enabled: false,
    description: "Renders client info on screen (FPS, CPS, ping)",
    settings: [
      { id: "show-fps", label: "Show FPS", type: "boolean", value: true },
      { id: "show-cps", label: "Show CPS", type: "boolean", value: true },
      { id: "show-ping", label: "Show Ping", type: "boolean", value: true },
      { id: "show-pos", label: "Show Position", type: "boolean", value: false },
    ],
  },
  {
    id: "target-hud", name: "Target HUD", category: "GUI", enabled: false,
    description: "Shows health/armor info for your current target",
  },
  {
    id: "click-gui", name: "Click GUI", category: "GUI", enabled: true,
    description: "This module panel — opens with RSHIFT",
    settings: [
      { id: "bind", label: "Keybind", type: "mode", value: "RSHIFT", options: ["RSHIFT", "RCTRL", "INSERT", "HOME"] },
    ],
  },
  {
    id: "friends", name: "Friends", category: "GUI", enabled: false,
    description: "Marks players as friends so modules ignore them",
    settings: [
      { id: "anti-attack", label: "Anti Attack", type: "boolean", value: true },
    ],
  },
  {
    id: "self-destruct", name: "Self Destruct", category: "GUI", enabled: false,
    description: "Removes the client from memory on trigger",
    settings: [
      { id: "bind", label: "Trigger Key", type: "mode", value: "DELETE", options: ["DELETE", "END", "F12"] },
    ],
  },
  // Inventory
  {
    id: "double-hand", name: "Double Hand", category: "Inventory", enabled: false,
    description: "Automatically fills offhand with items",
    settings: [
      { id: "item", label: "Item", type: "mode", value: "Totem", options: ["Totem", "Crystal", "Shield", "Gap"] },
    ],
  },
  {
    id: "auto-totem", name: "Auto Totem", category: "Inventory", enabled: false,
    description: "Keeps a totem of undying in your offhand",
    settings: [
      { id: "hotkey", label: "Only With Key", type: "boolean", value: false },
      { id: "hp-threshold", label: "HP Threshold", type: "slider", value: 4, min: 1, max: 20, step: 1 },
    ],
  },
  {
    id: "auto-pot", name: "Auto Pot", category: "Inventory", enabled: false,
    description: "Automatically drinks potions when HP is low",
    settings: [
      { id: "hp-threshold", label: "HP Threshold", type: "slider", value: 10, min: 1, max: 20, step: 1 },
      { id: "delay", label: "Delay (ms)", type: "slider", value: 50, min: 0, max: 500, step: 10 },
    ],
  },
  {
    id: "pot-refill", name: "Pot Refill", category: "Inventory", enabled: false,
    description: "Automatically refills potions from inventory",
  },
  {
    id: "totem-swap", name: "Totem Swap", category: "Inventory", enabled: false,
    description: "Instantly swaps in a new totem after it activates",
    settings: [
      { id: "delay", label: "Swap Delay (ms)", type: "slider", value: 0, min: 0, max: 200, step: 5 },
    ],
  },
  // Crystal
  {
    id: "anchor-aura", name: "Anchor Aura", category: "Crystal", enabled: false,
    description: "Automatically uses respawn anchors as weapons",
  },
  {
    id: "auto-crystal", name: "Auto Crystal", category: "Crystal", enabled: false,
    description: "Automatically places and breaks crystals",
    settings: [
      { id: "place-delay", label: "Place Delay", type: "slider", value: 0, min: 0, max: 20, step: 1 },
      { id: "break-delay", label: "Break Delay", type: "slider", value: 0, min: 0, max: 20, step: 1 },
      { id: "place-chance", label: "Place Chance %", type: "slider", value: 100, min: 0, max: 100, step: 1, description: "Randomization" },
      { id: "break-chance", label: "Break Chance %", type: "slider", value: 100, min: 0, max: 100, step: 1, description: "Randomization" },
      { id: "stop-on-kill", label: "Stop on Kill", type: "boolean", value: false, description: "Won't crystal if a dead player is nearby" },
      { id: "fake-punch", label: "Fake Punch", type: "boolean", value: false, description: "Will hit every entity if you miss a hit crystal" },
      { id: "click-simulation", label: "Click Simulation", type: "boolean", value: false, description: "Makes the CPS HUD think you're legit" },
      { id: "damage-tick", label: "Damage Tick", type: "boolean", value: false, description: "Times your crystals for a perfect d-tap" },
      { id: "anti-weakness", label: "Anti-Weakness", type: "boolean", value: false, description: "Silently switches to sword if you have weakness" },
    ],
  },
  {
    id: "crystal-aura", name: "Crystal Aura", category: "Crystal", enabled: false,
    description: "Full auto crystal PvP with placement prediction",
    settings: [
      { id: "range", label: "Place Range", type: "slider", value: 4.0, min: 1, max: 6, step: 0.1 },
      { id: "break-range", label: "Break Range", type: "slider", value: 5.0, min: 1, max: 8, step: 0.1 },
      { id: "rotate", label: "Rotate", type: "boolean", value: true },
    ],
  },
  {
    id: "crystal-optimizer", name: "Crystal Optimizer", category: "Crystal", enabled: false,
    description: "Optimizes crystal PvP",
    settings: [
      { id: "kill-chance", label: "Kill Chance %", type: "slider", value: 100, min: 0, max: 100, step: 1, description: "Chance to client-kill the crystal" },
      { id: "kill-delay-min", label: "Kill Delay Min (ms)", type: "slider", value: 0, min: 0, max: 200, step: 1, description: "Random ms wait before client-kill" },
      { id: "kill-delay-max", label: "Kill Delay Max (ms)", type: "slider", value: 0, min: 0, max: 200, step: 1 },
      { id: "max-reach", label: "Max Reach", type: "slider", value: 5.0, min: 3.0, max: 8.0, step: 0.1 },
      { id: "require-tool", label: "Require Tool", type: "boolean", value: true, description: "Only kill if holding a weapon/tool" },
      { id: "strength-bypass", label: "Strength Bypass", type: "boolean", value: true, description: "Skip weakness check while you have strength" },
    ],
  },
  {
    id: "double-anchor", name: "Double Anchor", category: "Crystal", enabled: false,
    description: "Uses two respawn anchors simultaneously",
  },
  {
    id: "hover-totem", name: "Hover Totem", category: "Crystal", enabled: false,
    description: "Holds totem in hand while crystalling",
    settings: [
      { id: "hp-threshold", label: "HP Threshold", type: "slider", value: 6, min: 1, max: 20, step: 1 },
    ],
  },
  {
    id: "auto-fireball", name: "Auto Fireball", category: "Crystal", enabled: false,
    description: "Automatically shoots fireballs at targets",
    settings: [
      { id: "range", label: "Range", type: "slider", value: 8, min: 2, max: 16, step: 1 },
      { id: "delay", label: "Delay (ms)", type: "slider", value: 100, min: 50, max: 1000, step: 10 },
    ],
  },
  {
    id: "auto-trap", name: "Auto Trap", category: "Crystal", enabled: false,
    description: "Traps players in obsidian boxes",
    settings: [
      { id: "range", label: "Range", type: "slider", value: 4, min: 2, max: 6, step: 0.5 },
      { id: "mode", label: "Mode", type: "mode", value: "Full", options: ["Full", "Partial", "Ceiling"] },
    ],
  },
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
  { name: "Crystal", icon: Zap },
];

function SettingsPanel({ module, onClose, onSettingChange }: {
  module: Module;
  onClose: () => void;
  onSettingChange: (moduleId: string, settingId: string, value: boolean | number | string) => void;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: 20 }}
      transition={{ duration: 0.2 }}
      className="w-72 border-l border-white/5 bg-[#080808] flex flex-col overflow-hidden"
    >
      <div className="flex items-center justify-between px-5 py-4 border-b border-white/5 bg-[#0a0a0a]">
        <div>
          <p className="text-[10px] text-white/30 font-mono tracking-widest uppercase mb-0.5">Settings</p>
          <h3 className="text-sm font-semibold text-cyan-400 tracking-wide">{module.name}</h3>
        </div>
        <button
          onClick={onClose}
          className="w-7 h-7 flex items-center justify-center text-white/30 hover:text-white/80 hover:bg-white/5 transition-colors"
          data-testid="button-close-settings"
        >
          <X className="w-4 h-4" />
        </button>
      </div>

      {module.description && (
        <div className="px-5 py-3 border-b border-white/5 bg-cyan-950/10">
          <p className="text-xs text-white/40 font-mono leading-relaxed">{module.description}</p>
        </div>
      )}

      <div className="flex-1 overflow-y-auto py-2">
        {!module.settings || module.settings.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-12 text-white/20">
            <Settings className="w-8 h-8 mb-3 opacity-30" />
            <p className="text-xs font-mono tracking-widest">NO SETTINGS</p>
          </div>
        ) : (
          module.settings.map((setting) => (
            <div key={setting.id} className="px-5 py-3 border-b border-white/[0.03] hover:bg-white/[0.02] transition-colors">
              <div className="flex items-center justify-between mb-1">
                <label className="text-[11px] text-white/60 font-mono">{setting.label}</label>
                {setting.type === "boolean" && (
                  <button
                    data-testid={`toggle-setting-${setting.id}`}
                    onClick={() => onSettingChange(module.id, setting.id, !(setting.value as boolean))}
                    className={`w-8 h-4 rounded-full border p-[1px] flex items-center transition-colors ${
                      setting.value ? "border-cyan-500/50 bg-cyan-900/30" : "border-white/10 bg-black/50"
                    }`}
                  >
                    <motion.div
                      className={`w-2.5 h-2.5 rounded-full ${setting.value ? "bg-cyan-400 shadow-[0_0_5px_rgba(34,211,238,1)]" : "bg-white/20"}`}
                      animate={{ x: setting.value ? 16 : 0 }}
                      transition={{ type: "spring", stiffness: 500, damping: 30 }}
                    />
                  </button>
                )}
                {setting.type === "slider" && (
                  <span className="text-[11px] text-cyan-400 font-mono">{Number(setting.value).toFixed(setting.step && setting.step < 1 ? 2 : 0)}</span>
                )}
              </div>
              {setting.description && (
                <p className="text-[10px] text-white/25 font-mono mb-2 leading-relaxed">{setting.description}</p>
              )}
              {setting.type === "slider" && (
                <input
                  data-testid={`slider-${setting.id}`}
                  type="range"
                  min={setting.min}
                  max={setting.max}
                  step={setting.step}
                  value={setting.value as number}
                  onChange={(e) => onSettingChange(module.id, setting.id, parseFloat(e.target.value))}
                  className="w-full h-1 appearance-none bg-white/10 rounded-full cursor-pointer accent-cyan-500"
                  style={{ accentColor: "#22d3ee" }}
                />
              )}
              {setting.type === "mode" && setting.options && (
                <div className="relative">
                  <select
                    data-testid={`select-${setting.id}`}
                    value={setting.value as string}
                    onChange={(e) => onSettingChange(module.id, setting.id, e.target.value)}
                    className="w-full bg-[#111] border border-white/10 text-white/70 text-[11px] font-mono px-3 py-1.5 appearance-none focus:outline-none focus:border-cyan-500/50 cursor-pointer"
                  >
                    {setting.options.map((opt) => (
                      <option key={opt} value={opt}>{opt}</option>
                    ))}
                  </select>
                  <ChevronDown className="absolute right-2 top-1/2 -translate-y-1/2 w-3 h-3 text-white/30 pointer-events-none" />
                </div>
              )}
            </div>
          ))
        )}
      </div>
    </motion.div>
  );
}

export default function Home() {
  const [modules, setModules] = useState<Module[]>(INITIAL_MODULES);
  const [activeCategory, setActiveCategory] = useState(CATEGORIES[0].name);
  const [search, setSearch] = useState("");
  const [selectedModule, setSelectedModule] = useState<string | null>(null);

  const toggleModule = (id: string) => {
    setModules(modules.map(m => m.id === id ? { ...m, enabled: !m.enabled } : m));
  };

  const handleSettingChange = (moduleId: string, settingId: string, value: boolean | number | string) => {
    setModules(modules.map(m => {
      if (m.id !== moduleId) return m;
      return {
        ...m,
        settings: m.settings?.map(s => s.id === settingId ? { ...s, value } : s),
      };
    }));
  };

  const filteredModules = useMemo(() => {
    if (search) {
      return modules.filter(m => m.name.toLowerCase().includes(search.toLowerCase()));
    }
    return modules.filter(m => m.category === activeCategory);
  }, [modules, activeCategory, search]);

  const activeCount = modules.filter(m => m.enabled).length;
  const openModule = selectedModule ? modules.find(m => m.id === selectedModule) : null;

  return (
    <div className="min-h-screen w-full bg-[#050505] text-white flex overflow-hidden selection:bg-cyan-500/30 font-sans relative">
      <div className="absolute top-[-20%] left-[-10%] w-[50%] h-[50%] bg-cyan-900/10 blur-[150px] pointer-events-none rounded-full" />
      <div className="absolute bottom-[-20%] right-[-10%] w-[50%] h-[50%] bg-cyan-900/10 blur-[150px] pointer-events-none rounded-full" />

      {/* Sidebar */}
      <div className="w-64 border-r border-white/5 bg-[#0a0a0a]/80 backdrop-blur-xl flex flex-col z-10 relative shrink-0">
        <div className="p-6 border-b border-white/5">
          <h1 className="text-4xl font-bold tracking-widest text-transparent bg-clip-text bg-gradient-to-r from-cyan-400 to-cyan-600 mb-1" style={{ textShadow: "0 0 20px rgba(34,211,238,0.3)" }}>
            ROCKY
          </h1>
          <p className="text-xs text-cyan-500/50 uppercase tracking-[0.2em] font-mono">Utility Client v2.0</p>
        </div>

        <div className="flex-1 overflow-y-auto py-4 px-3 space-y-1">
          {CATEGORIES.map((cat) => {
            const Icon = cat.icon;
            const isActive = !search && activeCategory === cat.name;
            const catActiveCount = modules.filter(m => m.category === cat.name && m.enabled).length;
            return (
              <button
                key={cat.name}
                data-testid={`nav-category-${cat.name.toLowerCase()}`}
                onClick={() => { setActiveCategory(cat.name); setSearch(""); setSelectedModule(null); }}
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
                <Icon className={`w-4 h-4 z-10 ${isActive ? "drop-shadow-[0_0_8px_rgba(34,211,238,0.8)]" : ""}`} />
                <span className="font-medium tracking-wider text-sm z-10">{cat.name.toUpperCase()}</span>
                {catActiveCount > 0 && (
                  <span className={`ml-auto text-xs font-mono z-10 ${isActive ? "text-cyan-400" : "text-white/40"}`}>
                    {catActiveCount}
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
      <div className="flex-1 flex flex-col z-10 relative min-w-0">
        <div className="h-20 border-b border-white/5 bg-[#0a0a0a]/50 backdrop-blur-md flex items-center px-8 justify-between shrink-0">
          <div className="flex items-center gap-3 text-white/50">
            <h2 className="text-xl font-semibold tracking-wider text-white">
              {search ? "SEARCH RESULTS" : activeCategory.toUpperCase()}
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
              data-testid="input-search"
              type="text"
              placeholder="SEARCH MODULES..."
              value={search}
              onChange={(e) => { setSearch(e.target.value); setSelectedModule(null); }}
              className="w-full bg-[#111] border border-white/10 text-white pl-10 pr-4 py-2 text-sm font-mono focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/50 transition-all placeholder:text-white/20"
            />
            {search && (
              <div className="absolute inset-0 border border-cyan-500/30 pointer-events-none animate-pulse" />
            )}
          </div>
        </div>

        <div className="flex-1 flex min-h-0">
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
                    <div
                      data-testid={`module-card-${module.id}`}
                      onContextMenu={(e) => { e.preventDefault(); setSelectedModule(selectedModule === module.id ? null : module.id); }}
                      className={`w-full group relative text-left p-4 border transition-all duration-300 overflow-hidden cursor-pointer ${
                        selectedModule === module.id
                          ? "border-white/30 bg-[#131313]"
                          : module.enabled
                          ? "bg-cyan-950/20 border-cyan-500/50 shadow-[0_0_15px_rgba(34,211,238,0.15)]"
                          : "bg-[#0f0f0f] border-white/5 hover:border-white/20 hover:bg-[#151515]"
                      }`}
                    >
                      <div className={`absolute top-0 right-0 w-4 h-4 border-t border-r transition-colors ${module.enabled ? "border-cyan-400" : "border-white/10 group-hover:border-white/30"}`} />

                      {module.enabled && (
                        <motion.div
                          className="absolute inset-0 bg-gradient-to-b from-transparent via-cyan-400/5 to-transparent h-[200%]"
                          animate={{ top: ["-100%", "100%"] }}
                          transition={{ duration: 2, repeat: Infinity, ease: "linear" }}
                        />
                      )}

                      <div className="flex justify-between items-start relative z-10">
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2 mb-1">
                            <h3 className={`font-semibold tracking-wide truncate ${module.enabled ? "text-cyan-400 drop-shadow-[0_0_5px_rgba(34,211,238,0.8)]" : "text-white/70"}`}>
                              {module.name}
                            </h3>
                          </div>
                          {search && (
                            <p className="text-[10px] text-white/30 font-mono tracking-widest uppercase truncate">
                              {module.category}
                            </p>
                          )}
                          {module.description && !search && (
                            <p className="text-[10px] text-white/25 font-mono leading-relaxed truncate mt-0.5">
                              {module.description}
                            </p>
                          )}
                        </div>

                        <div className="flex items-center ml-2 shrink-0">
                          <div className={`w-8 h-4 rounded-full border p-[1px] flex items-center transition-colors ${
                            module.enabled ? "border-cyan-500/50 bg-cyan-900/30" : "border-white/10 bg-black/50"
                          }`}>
                            <motion.div
                              className={`w-2.5 h-2.5 rounded-full ${module.enabled ? "bg-cyan-400 shadow-[0_0_5px_rgba(34,211,238,1)]" : "bg-white/20"}`}
                              animate={{ x: module.enabled ? 16 : 0 }}
                              transition={{ type: "spring", stiffness: 500, damping: 30 }}
                            />
                          </div>
                        </div>
                      </div>

                      {module.settings && module.settings.length > 0 && (
                        <div
                          data-testid={`button-open-settings-${module.id}`}
                          onClick={(e) => { e.stopPropagation(); setSelectedModule(selectedModule === module.id ? null : module.id); }}
                          className={`mt-2 relative z-10 flex items-center gap-1 text-[10px] font-mono transition-colors cursor-pointer w-fit ${selectedModule === module.id ? "text-white/60" : "text-white/20 hover:text-white/50"}`}
                        >
                          <Settings className="w-2.5 h-2.5" />
                          <span>{module.settings.length} SETTINGS</span>
                        </div>
                      )}

                      {/* Toggle click area */}
                      <div
                        className="absolute inset-0 z-0"
                        onClick={() => toggleModule(module.id)}
                      />
                    </div>
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

          {/* Settings Panel */}
          <AnimatePresence>
            {openModule && (
              <SettingsPanel
                module={openModule}
                onClose={() => setSelectedModule(null)}
                onSettingChange={handleSettingChange}
              />
            )}
          </AnimatePresence>
        </div>
      </div>
    </div>
  );
}
