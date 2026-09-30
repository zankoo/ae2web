package com.kokoda.ae2web.web.terminal;

import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.blockentity.WebTerminalBlockEntity;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing WebTerminal block entities.
 * Handles terminal registration, unregistration, and lookup.
 */
public final class TerminalRegistry {
    private static final TerminalRegistry instance = new TerminalRegistry();

    private final Map<String, WebTerminalBlockEntity> terminals = new ConcurrentHashMap<>();

    private TerminalRegistry() {
    }

    public static TerminalRegistry getInstance() {
        return instance;
    }

    public void register(WebTerminalBlockEntity terminal) {
        String terminalId = terminal.getTerminalId();
        terminals.put(terminalId, terminal);
        AE2WebMod.LOGGER.debug("Registered terminal: {}", terminalId);
    }

    public void unregister(WebTerminalBlockEntity terminal) {
        if (terminal == null) {
            return;
        }
        String terminalId = terminal.getTerminalId();
        if (terminalId == null || terminalId.isBlank()) {
            return;
        }
        // Only remove the entry if it is this block entity; another terminal may share the id.
        if (terminals.remove(terminalId, terminal)) {
            AE2WebMod.LOGGER.debug("Unregistered terminal: {}", terminalId);
        }
    }

    public void unregisterById(String terminalId) {
        if (terminalId == null || terminalId.isBlank()) {
            return;
        }
        terminals.remove(terminalId);
        AE2WebMod.LOGGER.debug("Unregistered terminal: {}", terminalId);
    }

    public Map<String, WebTerminalBlockEntity> getAll() {
        return Collections.unmodifiableMap(terminals);
    }

    public WebTerminalBlockEntity get(String id) {
        return terminals.get(id);
    }

    public WebTerminalBlockEntity getFirst() {
        return terminals.values().stream().findFirst().orElse(null);
    }

    public void clear() {
        terminals.clear();
    }
}
