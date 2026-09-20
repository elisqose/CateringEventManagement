package catering.businesslogic.event;

import java.time.LocalDate;

public class DailyFrequency extends AbstractFrequency {

    public DailyFrequency(LocalDate dataFine, Integer numeroOccorrenze) {
        super(dataFine, numeroOccorrenze);
    }

    public static DailyFrequency create(LocalDate dataFine, Integer numeroOccorrenze) {
        return new DailyFrequency(dataFine, numeroOccorrenze);
    }

    @Override
    public LocalDate prossimaOccorrenza(LocalDate dataCorrente) {
        return dataCorrente.plusDays(1);
    }

    @Override
    public String getTipo() {
        return "DAILY";
    }
}
