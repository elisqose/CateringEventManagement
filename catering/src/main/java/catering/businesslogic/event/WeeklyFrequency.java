package catering.businesslogic.event;

import java.time.LocalDate;

public class WeeklyFrequency extends AbstractFrequency {

    public WeeklyFrequency(LocalDate dataFine, Integer numeroOccorrenze) {
        super(dataFine, numeroOccorrenze);
    }

    public static WeeklyFrequency create(LocalDate dataFine, Integer numeroOccorrenze) {
        return new WeeklyFrequency(dataFine, numeroOccorrenze);
    }

    @Override
    public LocalDate prossimaOccorrenza(LocalDate dataCorrente) {
        return dataCorrente.plusWeeks(1);
    }

    @Override
    public String getTipo() {
        return "WEEKLY";
    }
}
