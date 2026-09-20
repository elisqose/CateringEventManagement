package catering.businesslogic.event;

import java.time.LocalDate;

public class MonthlyFrequency extends AbstractFrequency {

    public MonthlyFrequency(LocalDate dataFine, Integer numeroOccorrenze) {
        super(dataFine, numeroOccorrenze);
    }

    public static MonthlyFrequency create(LocalDate dataFine, Integer numeroOccorrenze) {
        return new MonthlyFrequency(dataFine, numeroOccorrenze);
    }

    @Override
    public LocalDate prossimaOccorrenza(LocalDate dataCorrente) {
        return dataCorrente.plusMonths(1);
    }

    @Override
    public String getTipo() {
        return "MONTHLY";
    }
}
