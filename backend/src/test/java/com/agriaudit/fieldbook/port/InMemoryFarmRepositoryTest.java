package com.agriaudit.fieldbook.port;

class InMemoryFarmRepositoryTest extends FarmRepositoryContract {

    private final InMemoryFarmRepository repository = new InMemoryFarmRepository();

    @Override
    protected FarmRepository repository() {
        return repository;
    }

    @Override
    protected long organizationA() {
        return 1;
    }

    @Override
    protected long organizationB() {
        return 2;
    }
}
