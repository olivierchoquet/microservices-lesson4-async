package com.ipl.product.service;

import com.ipl.product.api.dto.ProductDtos;
import com.ipl.product.domain.ProcessedOrder;                                  // +++ AJOUT
import com.ipl.product.domain.Product;
import com.ipl.product.messaging.events.OrderEvents.OrderCreatedEvent;         // +++ AJOUT
import com.ipl.product.messaging.events.OrderEvents.StockReservedEvent;        // +++ AJOUT
import com.ipl.product.repository.ProcessedOrderRepository;                    // +++ AJOUT
import com.ipl.product.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;                                                    // +++ AJOUT
import java.util.LinkedHashMap;                                                // +++ AJOUT
import java.util.List;
import java.util.Map;                                                          // +++ AJOUT

@Service
public class ProductService {

    private final ProductRepository repo;
    private final ProcessedOrderRepository processedRepo;                      // +++ AJOUT

    // ~~~ MODIFIE : un repository de plus dans le constructeur
    public ProductService(ProductRepository repo, ProcessedOrderRepository processedRepo) {
        this.repo = repo;
        this.processedRepo = processedRepo;
    }

    // =========================================================
    // CRUD : INCHANGE
    // =========================================================

    public List<Product> all() { return repo.findAll(); }

    public Product get(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product " + id + " not found"));
    }

    public Product create(ProductDtos.Create d) {
        return repo.save(new Product(d.name, d.price, d.stock));
    }

    public Product update(Long id, ProductDtos.Update d) {
        Product p = get(id);
        p.setName(d.name);
        p.setPrice(d.price);
        p.setStock(d.stock);
        return repo.save(p);
    }

    public void delete(Long id) { repo.delete(get(id)); }

    /*
     * CONSERVE : POST /products/{id}/reserve existe toujours.
     * Plus personne ne l'appelle depuis order-service, mais il reste utile
     * pour les tests manuels. A supprimer si vous voulez un decoupage strict.
     */
    @Transactional
    public void reserve(Long id, int qty) {
        Product p = get(id);
        if (p.getStock() < qty) throw new IllegalStateException("INSUFFICIENT_STOCK");
        p.setStock(p.getStock() - qty);
    }

    // =========================================================
    // +++ AJOUT : reservation de TOUTE la commande, en une transaction
    // =========================================================

    public boolean alreadyProcessed(Long orderId) {
        return processedRepo.existsById(orderId);
    }

    /*
     * C'est le gain le plus important de la refonte, et il n'a rien a voir
     * avec RabbitMQ : la reservation multi-lignes se fait desormais DANS
     * product-service, la ou vivent les donnees.
     *
     * Un seul @Transactional couvre toutes les lignes. Si la derniere echoue,
     * les precedentes sont annulees par la base. La reservation partielle
     * devient structurellement impossible, alors qu'aucune correction cote
     * order-service ne pouvait la garantir : @Transactional ne sait pas
     * annuler un appel HTTP deja parti.
     *
     * Retourne les lignes valorisees (nom + prix) pour que order-service
     * puisse completer sa commande sans avoir a rappeler product-service.
     */
    @Transactional
    public List<StockReservedEvent.PricedLine> reserveAll(Long orderId,
                                                          List<OrderCreatedEvent.Line> lines) {

        /*
         * Agregation par produit : corrige le bug des lignes en double.
         * La version synchrone validait chaque ligne isolement, donc deux
         * lignes de 30 sur un stock de 50 passaient toutes les deux.
         */
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (OrderCreatedEvent.Line l : lines) {
            wanted.merge(l.productId(), l.quantity(), Integer::sum);
        }

        // Phase 1 : tout verifier. Aucune ecriture a ce stade.
        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            Product p = get(e.getKey());                       // leve EntityNotFoundException
            if (p.getStock() < e.getValue()) {
                throw new IllegalStateException("INSUFFICIENT_STOCK for product " + e.getKey());
            }
        }

        // Phase 2 : tout appliquer. Meme transaction locale, donc tout ou rien.
        List<StockReservedEvent.PricedLine> priced = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            Product p = get(e.getKey());
            p.setStock(p.getStock() - e.getValue());
            priced.add(new StockReservedEvent.PricedLine(
                    p.getId(), p.getName(), e.getValue(), p.getPrice()));
        }

        /*
         * Marque la commande comme traitee DANS LA MEME TRANSACTION que la
         * decrementation : pas de trace sans effet, pas d'effet sans trace.
         */
        processedRepo.save(new ProcessedOrder(orderId));

        return priced;
    }
}
