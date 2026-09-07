package com.ipl.client.api;
import com.ipl.client.api.dto.ClientDtos;
import com.ipl.client.domain.Client;
import com.ipl.client.service.ClientService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@RestController @RequestMapping("/clients")
public class ClientController {
    private final ClientService service;

    public ClientController(ClientService s){
        this.service = s;
    }

    @GetMapping
    public List<ClientDtos.View> all(){
        return service.all().stream().map(c -> new ClientDtos.View(c.getId(),c.getName(),c.getAddress()))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientDtos.View> one(@PathVariable("id") Long id) {
        return service.findById(id)
                .map(c -> ResponseEntity.ok(new ClientDtos.View(c.getId(), c.getName(), c.getAddress())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientDtos.View create(@RequestBody @Valid ClientDtos.Create d){
        Client c = service.create(d);

        return new ClientDtos.View(c.getId(),c.getName(),c.getAddress());
    }

    @PutMapping("/{id}")
    public ClientDtos.View update(@PathVariable("id") Long id,@RequestBody @Valid ClientDtos.Update d){
        Client c=service.update(id,d);

        return new ClientDtos.View(c.getId(),c.getName(),c.getAddress());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id){
        service.delete(id);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String nf(Exception e){
        return e.getMessage();
    }
}
