package catering.businesslogic.event;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Frequenze di ricorrenza (Strategy)")
class AbstractFrequencyTest {

    @ParameterizedTest(name = "{0}: dal 15/06/2026 alla successiva {1}")
    @CsvSource({
            "DAILY,   2026-06-16",
            "WEEKLY,  2026-06-22",
            "MONTHLY, 2026-07-15"
    })
    @DisplayName("Ogni sottoclasse calcola il proprio passo")
    void prossimaOccorrenza_DependsOnTheConcreteFrequency(String tipo, String attesa) {
        AbstractFrequency frequenza = AbstractFrequency.create(tipo, null, 3);

        LocalDate successiva = frequenza.prossimaOccorrenza(LocalDate.of(2026, 6, 15));

        assertEquals(LocalDate.parse(attesa), successiva);
        assertEquals(tipo, frequenza.getTipo());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "DAILY,   DailyFrequency",
            "WEEKLY,  WeeklyFrequency",
            "MONTHLY, MonthlyFrequency"
    })
    @DisplayName("La factory restituisce la sottoclasse corrispondente")
    void create_ReturnsTheMatchingSubclass(String tipo, String sottoclasseAttesa) {
        AbstractFrequency frequenza = AbstractFrequency.create(tipo, null, 3);

        assertEquals(sottoclasseAttesa, frequenza.getClass().getSimpleName());
    }

    @Test
    @DisplayName("Una frequenza sconosciuta viene rifiutata")
    void create_UnknownType_Throws() {
        assertThrows(IllegalArgumentException.class, () -> AbstractFrequency.create("YEARLY", null, 3));
    }

    @Test
    @DisplayName("Una frequenza nulla viene rifiutata")
    void create_NullType_Throws() {
        assertThrows(IllegalArgumentException.class, () -> AbstractFrequency.create(null, null, 3));
    }
}
