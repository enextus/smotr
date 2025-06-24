package org.randomfetcher;

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
        frame.show(); // обязателен для AssertJ-Swing
    }

    @AfterEach
    void tearDown() {
        frame.cleanUp();
    }

    @Test
    void statusChangesAfterQrngClick() {
        frame.button("Get QRNG").click();

        // ждём, пока label начнёт с «QRNG», максимум 10 сек.
        pause(new Condition("status starts with QRNG") {
            @Override public boolean test() {
                return frame.label("statusLbl").text().startsWith("QRNG");
            }
        }, 10_000);

        // финальная проверка
        assertThat(frame.label("statusLbl").text())
                .startsWith("QRNG");
    }
}
