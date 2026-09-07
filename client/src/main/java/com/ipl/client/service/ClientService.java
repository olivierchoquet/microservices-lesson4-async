package com.ipl.client.service;
import com.ipl.client.api.dto.ClientDtos;
import com.ipl.client.domain.Client;
import com.ipl.client.repository.ClientRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class ClientService {
    private final ClientRepository repo;

    public ClientService(ClientRepository r){
        this.repo = r;
    }

    public List<Client> all(){
        return repo.findAll();
    }

    public Optional<Client> findById(Long id) { return repo.findById(id); }

    // “get or throw” for internal use
    public Client get(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Client " + id + " not found"));
    }

    public Client create(ClientDtos.Create d){
        return repo.save(new Client(d.name,d.address));
    }

    public Client update(Long id, ClientDtos.Update d){
        Client c = get(id);
        c.setName(d.name);
        c.setAddress(d.address);

        return repo.save(c);
    }

    public void delete(Long id){
        repo.delete(get(id));
    }
}
