package io.prismio.settings;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBCheckBox;
import io.prismio.toolchain.PrismioProjectSettings;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;

/**
 * The settings page promises what the plugin does. Turning the language server on or off, or pointing the plugin at
 * another compiler, is announced the moment the page is applied (the LSP side restarts the server on that message),
 * so the checkbox must not tell the user to restart the IDE.
 */
public class PrismioSettingsConfigurableTest extends BasePlatformTestCase {

  private static JBCheckBox checkBox(Component component) {
    if (component instanceof JBCheckBox box) {
      return box;
    }
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        JBCheckBox found = checkBox(child);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  public void testTheServerCheckboxDoesNotAskForAnIdeRestartBecauseApplyingRestartsTheServer() {
    PrismioSettingsConfigurable page = new PrismioSettingsConfigurable(getProject());
    JComponent component = page.createComponent();
    JBCheckBox box = checkBox(component);
    assertNotNull("the page has the language-server checkbox", box);
    assertFalse(box.getText(), box.getText().toLowerCase().contains("restart"));

    // What the label may promise: applying a change tells every listener, and the LSP one restarts the server.
    AtomicInteger changes = new AtomicInteger();
    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(PrismioSettingsListener.TOPIC,
        (PrismioSettingsListener) changes::incrementAndGet);
    PrismioProjectSettings.State state = PrismioProjectSettings.getInstance(getProject()).getState();
    page.reset();
    box.setSelected(!state.useLanguageServer);
    assertTrue(page.isModified());
    page.apply();
    assertEquals("applying announces the change at once", 1, changes.get());
    assertEquals(box.isSelected(), state.useLanguageServer);
    state.useLanguageServer = !state.useLanguageServer;
  }
}
