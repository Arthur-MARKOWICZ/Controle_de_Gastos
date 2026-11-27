package br.com.controlegastos.investments.application;

/** Posição inexistente ou de outro titular: as duas situações respondem 404. */
public class CdbPositionNotFoundException extends RuntimeException {

    public CdbPositionNotFoundException() {
        super("Posição de CDB não encontrada");
    }
}
