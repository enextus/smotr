package org.randomfetcher;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.finder.JOptionPaneFinder;

import org.assertj.swing.fixture.FrameFixture;

import org.assertj.swing.fixture.JOptionPaneFixture;
import org.assertj.swing.timing.Condition;
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.swing.timing.Pause.pause;
import static org.mockito.Mockito.*;

@Tag("ui")
class RandomFetcherUiIT {

    private FrameFixture frame;

    /* ── мок для RandomFetcher.fetchBytes(..) ───────────────────────────── */
    private AutoCloseable fetchStub;

    @BeforeEach
    void setUp() throws Exception {
        // подменяем статический метод, чтобы GUI не ходил в сеть
        fetchStub = mockStatic(RandomFetcher.class, invocation -> {
            if (invocation.getMethod().getName().equals("fetchBytes")) {
                int n = invocation.getArgument(0, Integer.class);
                // возвращаем «стаб» из n нулей
                return new int[n];
            }
            return invocation.callRealMethod();
        });

        RandomFetcherUI ui = GuiActionRunner.execute(
                () -> new RandomFetcherUI(new LogManager())
        );
        frame = new FrameFixture(ui);
        frame.show();                  // обязательно
    }

    @AfterEach
    void tearDown() throws Exception {
        frame.cleanUp();
        fetchStub.close();
    }

    /* ───────────────────────── tests ───────────────────────── */

    @Test
    void clickGetQrng_updatesStatusAndField() {
        frame.button("btnGetQrng").click();

        // ждём, пока статус поменяется
        pause(untilStatusStartsWith("QRNG"), 5_000);

        assertThat(frame.label("statusLbl").text()).startsWith("QRNG");
        assertThat(frame.textBox().text()).isNotEmpty();
    }

    @Test
    void clickClear_resetsStatusAndField() {
        // предварительно «наполняем» поле
        frame.button("btnGetQrng").click();
        pause(untilStatusStartsWith("QRNG"), 5_000);

        frame.button("btnClear").click();

        assertThat(frame.label("statusLbl").text())
                .isEqualTo("Select amount & press Get QRNG");
        assertThat(frame.textBox().text()).isEmpty();
    }

    @Test
    void clickAnalyse_opensResultDialog() {
        // нужно сначала получить последовательность
        frame.button("btnGetQrng").click();
        pause(untilStatusStartsWith("QRNG"), 5_000);

        frame.button("btnAnalyse").click();

        JOptionPaneFixture dlg = JOptionPaneFinder.findOptionPane()
                .withTimeout(TimeUnit.SECONDS.toMillis(5))
                .using(frame.robot());
        dlg.requireVisible();
        dlg.button().click();          // закрываем OK
    }



    @Test
    void windowClose_disposesFrame() {
        frame.close();
        assertThat(frame.target().isDisplayable()).isFalse();
    }

    /* ───────────────────────── helpers ───────────────────────── */

    private Condition untilStatusStartsWith(String prefix) {
        return new Condition("status starts with " + prefix) {
            @Override public boolean test() {
                return frame.label("statusLbl").text().startsWith(prefix);
            }
        };
    }


}
