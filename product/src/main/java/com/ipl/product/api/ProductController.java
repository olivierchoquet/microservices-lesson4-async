package com.ipl.product.api;
import com.ipl.product.api.dto.ProductDtos;
import com.ipl.product.domain.Product;
import com.ipl.product.service.ProductService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@RestController @RequestMapping("/products")
public class ProductController {
    private final ProductService service;
    public ProductController(ProductService s){ this.service=s; }

    @GetMapping
    public List<ProductDtos.View> all() {
        return service.all().stream()
                .map(p -> new ProductDtos.View(p.getId(), p.getName(), p.getPrice(), p.getStock()))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ProductDtos.View one(@PathVariable("id") Long id){
        Product p = service.get(id);
        return new ProductDtos.View(p.getId(),p.getName(),p.getPrice(),p.getStock());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDtos.View create(@RequestBody @Valid ProductDtos.Create d){
        Product p=service.create(d);
        return new ProductDtos.View(p.getId(),p.getName(),p.getPrice(),p.getStock());
    }

    @PutMapping("/{id}")
    public ProductDtos.View update(@PathVariable("id") Long id,@RequestBody @Valid ProductDtos.Update d){
        Product p=service.update(id,d);
        return new ProductDtos.View(p.getId(),p.getName(),p.getPrice(),p.getStock());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id){
        service.delete(id);
    }

    @PostMapping("/{id}/reserve")
    @ResponseStatus(HttpStatus.NO_CONTENT) public void reserve(@PathVariable("id") Long id,@RequestBody @Valid ProductDtos.Reserve d){
        service.reserve(id,d.quantity);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String nf(Exception e){
        return e.getMessage();
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict(Exception e){
        return e.getMessage();
    }
}
