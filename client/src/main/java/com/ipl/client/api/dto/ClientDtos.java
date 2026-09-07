package com.ipl.client.api.dto;
import jakarta.validation.constraints.NotBlank;

public class ClientDtos {
    public static class Create {
        @NotBlank
        public String name;

        @NotBlank
        public String address;
    }

    public static class Update extends Create {}

    public static class View {
        public Long id;
        public String name;
        public String address;

        public View(){}

        public View(Long id,String n,String a){
            this.id=id;
            this.name=n; this.address=a;
        }
    }
}
