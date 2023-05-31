package com.orderservice.service;

import com.orderservice.entity.Order;
import com.orderservice.entity.OrderLineItem;
import com.orderservice.mapper.OrderLineItemMapper;
import com.orderservice.mapper.OrderMapper;
import com.orderservice.model.InventoryResponse;
import com.orderservice.model.OrderDTO;
import com.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OrderLineItemMapper orderLineItemMapper;
    private final WebClient.Builder webClient;

    public List<OrderDTO> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(orderMapper::toDTO)
                .collect(Collectors.toList());

    }

    public OrderDTO getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        return orderMapper.toDTO(order);
    }

    public OrderDTO createOrder(OrderDTO orderDTO) {
        Order order = Order.builder()
                .orderNumber(UUID.randomUUID().toString())
                .description(orderDTO.getDescription())
                .build();

        List<OrderLineItem> lineItems = orderDTO.getItemsList().stream()
                .map(orderLineItemMapper::toEntity)
                .collect(Collectors.toList());
        order.setItemsList(lineItems);

        List<String> itemCodes = order.getItemsList().stream()
                .map(OrderLineItem::getItemCode)
                .toList();

        InventoryResponse[] inventoryResponses = webClient.build().get()
                .uri("http://Inventory-Service/inventory",
                        uriBuilder -> uriBuilder.queryParam("itemCode", itemCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();

        boolean productsInStock = Arrays.stream(inventoryResponses).allMatch(InventoryResponse::isInStock);

        boolean allItemsExistInInventory = new HashSet<>(Arrays.stream(inventoryResponses)
                .map(InventoryResponse::getItemCode)
                .collect(Collectors.toList()))
                .containsAll(itemCodes);

        if (productsInStock && allItemsExistInInventory) {
            return orderMapper.toDTO(orderRepository.save(order));
        } else {
            if (!allItemsExistInInventory) {
                throw new IllegalArgumentException("One or more items do not exist in inventory");
            } else {
                throw new IllegalArgumentException("One or more products are out of stock");
            }
        }
    }


    public void updateOrder(OrderDTO orderDTO) {
        orderMapper.toDTO(orderRepository.save(orderMapper.toEntity(orderDTO)));
    }

    public void deleteOrder(Long orderId) {
        orderRepository.deleteById(orderId);
    }
}
