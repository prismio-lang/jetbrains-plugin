package io.prismio.symbols;

import junit.framework.TestCase;

/**
 * {@code async fn} keeps its function visible to navigation and completion, in every place a
 * function can be declared, and the call of one is not given its return type: it is a
 * {@code Future} of it, which the plugin does not model, until an {@code await}.
 */
public class AsyncDeclarationsTest extends TestCase {

  private static ModuleSummary parse(String source) {
    return SignatureParser.parse(source, "app");
  }

  public void testAsyncFnIsVisibleAtTopLevelAndAfterAVisibilityMarker() {
    ModuleSummary summary = parse("""
        async fn fetchUser(id: Int) -> User {
            return User { id: id }
        }

        public async fn double(x: Int) -> Int {
            return x * 2
        }

        fn plain() -> Int {
            return 1
        }
        """);
    assertEquals(3, summary.functions().size());
    assertTrue(summary.functions().get(0).async());
    assertEquals("fetchUser", summary.functions().get(0).name());
    assertTrue(summary.functions().get(1).async());
    assertEquals(FnSig.PUBLIC, summary.functions().get(1).visibility());
    assertFalse(summary.functions().get(2).async());
  }

  public void testAsyncMethodInAnImplBlockIsVisible() {
    ModuleSummary summary = parse("""
        impl Client {
            async fn get(self, id: Int) -> String {
                return self.base
            }

            fn size(self) -> Int {
                return 1
            }
        }
        """);
    assertEquals(2, summary.functions().size());
    assertTrue(summary.functions().get(0).async());
    assertFalse(summary.functions().get(1).async());
  }

  public void testAsyncIsNotLeftOnTheNextFunction() {
    ModuleSummary summary = parse("""
        async fn a() -> Int { return 1 }
        fn b() -> Int { return 2 }
        """);
    assertTrue(summary.functions().get(0).async());
    assertFalse(summary.functions().get(1).async());
  }
}
