package com.sis.dto;

import javax.validation.constraints.NotNull;

public class UpdateAcademicTermRegistrationWindowRequest {
    @NotNull
    private Boolean registrationOpen;

    public Boolean getRegistrationOpen() { return registrationOpen; }
    public void setRegistrationOpen(Boolean registrationOpen) { this.registrationOpen = registrationOpen; }
}
