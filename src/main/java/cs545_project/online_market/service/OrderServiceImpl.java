package cs545_project.online_market.service;

import cs545_project.online_market.controller.request.OrderRequest;
import cs545_project.online_market.controller.response.AddressResponse;
import cs545_project.online_market.controller.response.OrderItemResponse;
import cs545_project.online_market.controller.response.OrderResponse;
import cs545_project.online_market.domain.BillingAddress;
import cs545_project.online_market.domain.Order;
import cs545_project.online_market.domain.OrderDetails;
import cs545_project.online_market.domain.ShippingAddress;
import cs545_project.online_market.domain.User;
import cs545_project.online_market.repository.OrderRepository;
import cs545_project.online_market.repository.ProductRepository;
import cs545_project.online_market.repository.UserRepository;
import org.hashids.Hashids;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author knguyen93
 */
@Service
@Transactional
public class OrderServiceImpl implements OrderService {
    private UserRepository userRepository;
    private ProductRepository productRepository;
    private OrderRepository orderRepository;
    private Hashids hashids;

    @Autowired
    public OrderServiceImpl(UserRepository userRepository, ProductRepository productRepository, OrderRepository orderRepository, Hashids hashids) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.hashids = hashids;
    }

    @Override
    public OrderResponse makeOrder(String buyerUserName, OrderRequest request) {
        User buyer = this.userRepository.findByUsername(buyerUserName)
            .orElseThrow(() -> new IllegalArgumentException("Buyer not found"));

        if (request.isApplyCoupon() && buyer.getPoints() == 0) {
            throw new IllegalArgumentException("No coupon available for Buyer");
        }

        Order order = generateOrder(buyer, request);

        if (request.isApplyCoupon()) {
            double total = order.total(), credit = 0, remainPoints = 0;

            if (total <= buyer.getPoints()) {
                remainPoints = buyer.getPoints() - total;
            } else {
                credit = order.total() - buyer.getPoints();
            }

            buyer.setPoints(remainPoints);
            order.setCredit(credit);
        }

        userRepository.save(buyer);
        return mapToOrderResponse(orderRepository.save(order));
    }

    private Order generateOrder(User buyer, OrderRequest request) {
        Order order = new Order();
        List<OrderDetails> items = request.getItemRequests()
            .stream()
            .map(this::generateOrderDetails)
            .collect(Collectors.toList());

        ShippingAddress shippingAddress = buyer.getShippingAddresses()
            .stream()
            .filter(addr -> addr.getId() == request.getShippingAddress().getId())
            .findFirst()
            .orElseGet(() -> {
                ShippingAddress address = new ShippingAddress();
                BeanUtils.copyProperties(request.getShippingAddress(), address);
                return address;
            });

        BillingAddress billingAddress = buyer.getBillingAddresses()
            .stream()
            .filter(addr -> addr.getId() == request.getShippingAddress().getId())
            .findFirst()
            .orElseGet(() -> {
                BillingAddress address = new BillingAddress();
                BeanUtils.copyProperties(request.getBillingAddress(), address);
                return address;
            });
        order.setBillingAddress(billingAddress);
        order.setShippingAddress(shippingAddress);
        order.setOrderDetails(items);

        return order;
    }

    private OrderDetails generateOrderDetails(OrderRequest.ItemRequest itemRequest) {
        return this.productRepository
            .findById(itemRequest.getProductId())
            .map(prod -> new OrderDetails(prod, itemRequest.getQuantity(), prod.getPrice()))
            .orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }

    private OrderResponse mapToOrderResponse(Order order) {
        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setOrderCode(hashids.encode(order.getId()));
        orderResponse.setBillingAddress(this.mapToBillingAddressResponse(order.getBillingAddress()));
        orderResponse.setShippingAddress(this.mapToShippingAddressResponse(order.getShippingAddress()));
        orderResponse.setCredit(order.getCredit());
        orderResponse.setPoints(order.getPoints());
        orderResponse.setTotal(order.total());
        orderResponse.setOrderItems(
            order.getOrderDetails()
            .stream()
            .map(this::mapToOrderItemResponse)
            .collect(Collectors.toList())
        );
        return orderResponse;
    }

    private OrderItemResponse mapToOrderItemResponse(OrderDetails orderDetails) {
        OrderItemResponse orderItemResponse = new OrderItemResponse();
        orderItemResponse.setPrice(orderDetails.getPrice());
        orderItemResponse.setQuantity(orderDetails.getQuantity());
        orderItemResponse.setProductName(orderDetails.getProduct().getName());
        orderItemResponse.setImage( orderDetails.getProduct().getImage());
        return orderItemResponse;
    }

    private AddressResponse mapToBillingAddressResponse(BillingAddress billingAddress) {
        AddressResponse response = new AddressResponse();
        BeanUtils.copyProperties(billingAddress, response);
        return response;
    }

    private AddressResponse mapToShippingAddressResponse(ShippingAddress shippingAddress) {
        AddressResponse response = new AddressResponse();
        BeanUtils.copyProperties(shippingAddress, response);
        return response;
    }
}
