package catering.businesslogic.event;

import java.time.LocalDate;

public class ModificaEventoCommand implements EventCommand {

    private final String luogo;
    private final LocalDate dataInizio;
    private final Integer durata;
    private final Integer numeroServiziRichiesti;
    private final Integer numeroPartecipanti;
    private final String note;
    private final String cliente;

    public ModificaEventoCommand(String luogo, LocalDate dataInizio, Integer durata,
            Integer numeroServiziRichiesti, Integer numeroPartecipanti, String note, String cliente) {
        this.luogo = luogo;
        this.dataInizio = dataInizio;
        this.durata = durata;
        this.numeroServiziRichiesti = numeroServiziRichiesti;
        this.numeroPartecipanti = numeroPartecipanti;
        this.note = note;
        this.cliente = cliente;
    }

    @Override
    public boolean isApplicabile(Event e) {
        return e.isModificabile();
    }

    @Override
    public void execute(Event e) {
        e.aggiornaAttributi(luogo, dataInizio, durata, numeroServiziRichiesti, numeroPartecipanti, note, cliente);
    }
}
