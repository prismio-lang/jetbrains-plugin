package io.prismio.settings;

import com.intellij.util.messages.Topic;

/**
 * Told when the project's Prismio settings were applied with a different compiler or a different choice
 * about the language server, so what runs from them can start again. A topic rather than a call: the
 * listener that restarts the server lives in the part of the plugin that exists only where the platform
 * has an LSP client.
 */
public interface PrismioSettingsListener {

  Topic<PrismioSettingsListener> TOPIC =
      Topic.create("Prismio settings", PrismioSettingsListener.class);

  void toolchainSettingsChanged();
}
