package org.randomfetcher;

import org.assertj.swing.core.matcher.JButtonMatcher;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.timing.Condition;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.swing.timing.Pause.pause;

@Tag("ui")
class RandomFetcherUiTests {

    private FrameFixture frame;

    @BeforeEach
    void setUp() {
        RandomFetcherUI ui = GuiActionRunner.execute(
                () -> new RandomFetcherUI(new LogManager())
        );
        frame = new FrameFixture(ui);
        frame.show();               // обязательно!
    }

    @AfterEach
    void tearDown() { frame.cleanUp(); }

    @Test
    void statusChangesAfterQrngClick() {
        // кликаем по имени, а не по тексту
        frame.button("btnGetQrng").click();

        pause(new Condition("status starts with QRNG") {
            @Override public boolean test() {
                return frame.label("statusLbl").text().startsWith("QRNG");
            }
        }, 10_000);

        assertThat(frame.label("statusLbl").text()).startsWith("QRNG");
    }
}
