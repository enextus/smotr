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

/**
 * Интеграционные тесты Swing-интерфейса RandomFetcher с помощью AssertJ-Swing.
 *
 * <ul>
 *   <li>Все сетевые вызовы мокаются (static-mock RandomFetcher.fetchBytes).</li>
 *   <li>Перед стартом тестов заполняем поле <b>API-ключа</b>
 *       – иначе кнопка «Analyse» остаётся заблокированной.</li>
 *   <li>Имя каждого интересующего компонента задано в UI через
 *       {@code setName(...)}; тесты ищут именно по имени.</li>
 * </ul>
 */
@Tag("ui")
class RandomFetcherUiIT {

    /* ---------- поля ---------- */

    private FrameFixture frame;          // «обёртка» окна для AssertJ-Swing
    private AutoCloseable fetchStub;     // static-mock QRNG

    /* ===================================================================
     *                     1.  S E T U P  /  T E A R D O W N
     * =================================================================== */

    @BeforeEach
    void setUp() throws Exception {
        /* 1.1 Мокаем RandomFetcher.fetchBytes(int) → массив нулей */
        fetchStub = mockStatic(RandomFetcher.class, invocation -> {
            if ("fetchBytes".equals(invocation.getMethod().getName())) {
                int n = invocation.getArgument(0, Integer.class);
                return new int[n];
            }
            return invocation.callRealMethod();
        });

        /* 1.2 Создаём GUI внутри EDT */
        RandomFetcherUI ui = GuiActionRunner.execute(() -> {
            RandomFetcherUI w = new RandomFetcherUI(new LogManager());
            w.initUI();                     // обязательный вызов!
            return w;
        });

        /* 1.3 Оборачиваем окно для AssertJ-Swing */
        frame = new FrameFixture(ui);
        frame.show();                       // окно должно быть «showing»

        /* 1.4 Заполняем поле с API-ключом (name="apiKeyField")  ★ */
        frame.textBox("apiKeyField").setText("sk-dummy-key");
    }

    @AfterEach
    void tearDown() throws Exception {
        frame.cleanUp();    // освобождаем ресурсы AssertJ-Swing
        fetchStub.close();  // снимаем static-mock
    }

    /* ===================================================================
     *                           2.  Т Е С Т Ы
     * =================================================================== */

    /** «Get QRNG» → статус меняется, поле sequence не пустое. */
    @Test
    void clickGetQrng_updatesStatusAndField() {
        frame.button("btnGetQrng").click();

        pause(untilStatusStartsWith("QRNG"), 5_000);

        assertThat(frame.label("statusLbl").text()).startsWith("QRNG");
        assertThat(frame.textBox("sequenceField").text()).isNotEmpty();
    }

    /** «Clear» возвращает дефолтный статус и очищает поле. */
    @Test
    void clickClear_resetsStatusAndField() {
        frame.button("btnGetQrng").click();
        pause(untilStatusStartsWith("QRNG"), 5_000);

        frame.button("btnClear").click();

        assertThat(frame.label("statusLbl").text())
                .isEqualTo("Select size & press Get QRNG");
        assertThat(frame.textBox("sequenceField").text()).isEmpty();
    }

    /** «Analyse» открывает модальный диалог с отчётом. */
    @Test
    void clickAnalyse_opensResultDialog() {
        frame.button("btnGetQrng").click();
        pause(untilStatusStartsWith("QRNG"), 5_000);

        frame.button("btnAnalyse").click();

        JOptionPaneFixture dlg = JOptionPaneFinder.findOptionPane()
                .withTimeout(TimeUnit.SECONDS.toMillis(5))
                .using(frame.robot());
        dlg.requireVisible();
        dlg.button().click();    // закрываем диалог
    }

    /** Закрытие окна должно приводить к dispose(). */
    @Test
    void windowClose_disposesFrame() {
        frame.close();
        assertThat(frame.target().isDisplayable()).isFalse();
    }

    /* ===================================================================
     *                           3.  Х Е Л П Е Р Ы
     * =================================================================== */

    /** Условие ожидания, пока statusLbl начинается с prefix. */
    private Condition untilStatusStartsWith(String prefix) {
        return new Condition("status starts with " + prefix) {
            @Override public boolean test() {
                return frame.label("statusLbl").text().startsWith(prefix);
            }
        };
    }
}
