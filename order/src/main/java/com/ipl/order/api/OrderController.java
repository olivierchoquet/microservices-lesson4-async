package com.ipl.order.api;

import com.ipl.order.api.dto.OrderDtos;
import com.ipl.order.api.dto.OrderView;
import com.ipl.order.service.OrderService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService s) {
        this.service = s;
    }

    /*
     * ~~~ MODIFIE : 201 Created devient 202 Accepted.
     *
     * 201 signifie "la ressource est creee et l'operation est terminee".
     * Ce n'est plus vrai : la commande existe, mais le stock n'est pas
     * encore reserve. 202 dit exactement cela : "j'ai pris votre demande,
     * le resultat viendra plus tard". Le client relit la commande pour
     * connaitre son statut final.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)     // ~~~ MODIFIE, etait HttpStatus.CREATED
    public OrderView create(@RequestBody @Valid OrderDtos.Create req) {
        return service.create(req);
    }

    @GetMapping("/{id}")                     // INCHANGE
    public OrderView one(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @GetMapping                              // INCHANGE
    public List<OrderView> list(@RequestParam(name = "clientId", required = false) Long clientId) {
        return clientId == null ? service.all() : service.byClient(clientId);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String nf(Exception e) {
        return e.getMessage();
    }

    /*
     * ~~~ A CORRIGER : bug herite de la version synchrone.
     *
     * Ce handler attrape TOUTE RuntimeException et repond 409, ce qui
     * transforme aussi les vrais bugs (NullPointerException, panne de base)
     * en conflit metier. Conserve tel quel pour que la comparaison avec la
     * version synchrone reste lisible, mais c'est a corriger.
     *
     * A noter : en asynchrone il ne concerne plus le stock, puisque
     * INSUFFICIENT_STOCK n'est plus une exception mais un evenement.
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict(Exception e) {
        return e.getMessage();
    }
}
