package com.ipl.order.domain;

/*
 * ~~~ MODIFIE
 *
 * L'enum existait deja dans la version synchrone, mais ne servait a rien :
 * ClientOrder mettait PENDING au constructeur, il n'y avait pas de getter,
 * OrderView ne l'exposait pas, et rien ne le changeait jamais.
 *
 * En asynchrone il devient le coeur du modele : c'est le seul moyen de
 * repondre a l'utilisateur avant que le stock ne soit reserve.
 */
public enum OrderStatus {
    PENDING,        // CONSERVE : commande acceptee, stock pas encore reserve
    CONFIRMED,      // +++ AJOUT : product-service a reserve le stock
    REJECTED        // +++ AJOUT : stock insuffisant ou produit inexistant

    // --- SUPPRIMES : CREATED et APPROVED, jamais utilises
}
