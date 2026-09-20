package catering.businesslogic.event;

import java.time.LocalDate;

public abstract class AbstractFrequency {

    protected LocalDate dataFine;
    protected Integer numeroOccorrenze;

    protected AbstractFrequency(LocalDate dataFine, Integer numeroOccorrenze) {
        this.dataFine = dataFine;
        this.numeroOccorrenze = numeroOccorrenze;
    }

    public abstract LocalDate prossimaOccorrenza(LocalDate dataCorrente);

    public abstract String getTipo();

    public LocalDate getDataFine() {
        return dataFine;
    }

    public void setDataFine(LocalDate dataFine) {
        this.dataFine = dataFine;
    }

    public Integer getNumeroOccorrenze() {
        return numeroOccorrenze;
    }

    public void setNumeroOccorrenze(Integer numeroOccorrenze) {
        this.numeroOccorrenze = numeroOccorrenze;
    }

    public static AbstractFrequency create(String tipoFrequenza, LocalDate dataFine, Integer numeroOccorrenze) {
        if (tipoFrequenza == null) {
            throw new IllegalArgumentException("tipoFrequenza is required");
        }
        switch (tipoFrequenza.toUpperCase()) {
            case "DAILY":
                return new DailyFrequency(dataFine, numeroOccorrenze);
            case "WEEKLY":
                return new WeeklyFrequency(dataFine, numeroOccorrenze);
            case "MONTHLY":
                return new MonthlyFrequency(dataFine, numeroOccorrenze);
            default:
                throw new IllegalArgumentException("Unknown tipoFrequenza: " + tipoFrequenza);
        }
    }
}
