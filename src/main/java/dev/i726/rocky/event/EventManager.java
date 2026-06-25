package dev.i726.rocky.event;

import dev.i726.rocky.Rocky;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

public final class EventManager {
        private final HashMap<Class<? extends Listener>, ArrayList<PrioritizedListener<? extends Listener>>> listenerMap;

        public EventManager() {
                listenerMap = new HashMap<>();
        }

        public static <L extends Listener, E extends Event<L>> void fire(E event) {
                if (Rocky.INSTANCE != null) {
                        EventManager eventManager = Rocky.INSTANCE.getEventManager();
                        if (eventManager != null) {
                                eventManager.fireImpl(event);
                        }
                }
        }

        private <L extends Listener, E extends Event<L>> void fireImpl(E event) {
                ArrayList<PrioritizedListener<L>> listeners =
                        (ArrayList<PrioritizedListener<L>>) (Object) listenerMap.get(event.getListenerType());

                if (listeners == null || listeners.isEmpty()) return;

                // Snapshot for safe iteration — one allocation, no sort (sorted on add).
                ArrayList<L> snapshot = new ArrayList<>(listeners.size());
                for (PrioritizedListener<L> pl : listeners) {
                        if (pl != null) snapshot.add(pl.getListener());
                }

                event.fire(snapshot);
        }

        public <L extends Listener> void add(Class<L> type, L listener) {
                add(type, listener, 0);
        }

        public <L extends Listener> void add(Class<L> type, L listener, int priority) {
                ArrayList<PrioritizedListener<L>> listeners =
                        (ArrayList<PrioritizedListener<L>>) (Object) listenerMap.get(type);
                if (listeners == null) {
                        listeners = new ArrayList<>();
                        listenerMap.put(type, (ArrayList<PrioritizedListener<? extends Listener>>) (Object) listeners);
                }
                listeners.add(new PrioritizedListener<>(listener, priority));
                // Sort once on add so fireImpl never needs to sort.
                listeners.sort(Comparator.comparingInt(l -> Integer.MAX_VALUE - l.getPriority()));
        }

        public <L extends Listener> void remove(Class<L> type, L listener) {
                ArrayList<PrioritizedListener<L>> listeners =
                        (ArrayList<PrioritizedListener<L>>) (Object) listenerMap.get(type);
                if (listeners != null)
                        listeners.removeIf(l -> l.getListener().equals(listener));
        }

        private static class PrioritizedListener<L extends Listener> {
                private final L listener;
                private final int priority;

                public PrioritizedListener(L listener, int priority) {
                        this.listener = listener;
                        this.priority = priority;
                }

                public int getPriority() { return priority; }
                public L getListener()   { return listener; }
        }
}
