package org.example.amartha.loan.state;

/**
 * DISBURSED — terminal state. No further transitions are possible.
 * All operations inherit the default rejection from {@link AbstractLoanState}.
 */
public final class DisbursedState extends AbstractLoanState {

    public static final DisbursedState INSTANCE = new DisbursedState();

    private DisbursedState() {}

    @Override
    protected String stateName() { return "DISBURSED"; }
}
