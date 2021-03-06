package cs545_project.online_market.service;

import cs545_project.online_market.controller.request.OrderRequest;
import cs545_project.online_market.controller.response.AddressResponse;
import cs545_project.online_market.controller.response.OrderItemResponse;
import cs545_project.online_market.controller.response.OrderResponse;
import cs545_project.online_market.domain.*;
import cs545_project.online_market.helper.Util;
import cs545_project.online_market.repository.OrderDetailRepository;
import cs545_project.online_market.repository.OrderRepository;
import cs545_project.online_market.repository.ProductRepository;
import cs545_project.online_market.repository.UserRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.context.Context;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
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
    private Util util;
    private EmailService emailService;
    private OrderDetailRepository orderDetailRepository;

    @Autowired
    public OrderServiceImpl(UserRepository userRepository, ProductRepository productRepository,
                            OrderRepository orderRepository, Util util, EmailService emailService,
                            OrderDetailRepository orderDetailRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.util = util;
        this.emailService = emailService;
        this.orderDetailRepository = orderDetailRepository;
    }

    @Override
    public OrderResponse placeOrder(OrderRequest request, Cart cart) {
        User buyer = Optional.ofNullable(util.getCurrentUser())
            .orElseThrow(() -> new IllegalArgumentException("Buyer not found"));

        if (request.isApplyCoupon() && buyer.getPoints() == 0) {
            throw new IllegalArgumentException("No coupon available for Buyer");
        }

        Order order = generateOrder(buyer, request, cart);

        double total = order.total(), credit = 0, remainPoints = 0;
        if (request.isApplyCoupon()) {
            if (total <= buyer.getAvailablePointsCredit()) {
                remainPoints = buyer.getAvailablePointsCredit() - total;
            } else {
                credit = order.total() - buyer.getAvailablePointsCredit();
            }
        } else {
            credit = total;
        }

        order.setCard(
            buyer.getCards()
                .stream()
                .filter(c -> c.getId().equals(request.getPaymentCard()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid Payment Card"))
        );
        buyer.setPoints(remainPoints * 100 + credit);
        order.setCredit(credit);
        order.setBuyer(buyer);
        buyer.addOrder(order);
        userRepository.save(buyer); // Save Order
        productRepository.saveAll(
            order.getOrderDetails()
                .stream()
                .map(orderItem -> {
                    Product product = orderItem.getProduct();
                    product.setInUse(true);
                    return product;
                })
                .collect(Collectors.toList())); // Update Products Stock

        OrderResponse orderResponse = mapToOrderResponse(order);
        this.emailService.sendConfirmPurchaseMessage(orderResponse);

        return orderResponse;
    }

    @Override
    public List<OrderResponse> getOrdersOfCurrentUser() {
        return util.getCurrentUser().getOrders()
            .stream()
            .map(this::mapToOrderResponse)
            .collect(Collectors.toList());
    }

    private Order generateOrder(User buyer, OrderRequest request, Cart cart) {
        Order order = new Order();
        List<OrderDetails> items = cart.getCartItems()
            .values()
            .stream()
            .map(this::generateOrderDetails)
            .collect(Collectors.toList());
        order.setOrderDetails(items);

        ShippingAddress shippingAddress = buyer.getShippingAddresses()
            .stream()
            .filter(addr -> addr.getId().equals(request.getShippingAddress()))
            .findFirst()
            .orElseGet(() -> {
                ShippingAddress address = new ShippingAddress();
                BeanUtils.copyProperties(request.getShippingAddress(), address);
                return address;
            });

        BillingAddress billingAddress = buyer.getBillingAddresses()
            .stream()
            .filter(addr -> addr.getId().equals(request.getBillingAddress()))
            .findFirst()
            .orElseGet(() -> {
                BillingAddress address = new BillingAddress();
                BeanUtils.copyProperties(request.getBillingAddress(), address);
                return address;
            });
        order.setBillingAddress(billingAddress);
        order.setShippingAddress(shippingAddress);
        order.setReceiver(request.getReceiver());
        return order;
    }

    private OrderDetails generateOrderDetails(CartItem cartItem) {
        return this.productRepository
            .findById(cartItem.getProduct().getId())
            .map(prod -> {
                if (prod.getStock() < cartItem.getQuantity()) {
                    throw new IllegalArgumentException(
                        String.format("Product name %s only has %d item left", prod.getName(), prod.getStock()));
                }

                prod.setStock(prod.getStock() - cartItem.getQuantity());
                return new OrderDetails(prod, cartItem.getQuantity(), prod.getPrice());
            })
            .orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }

    private OrderResponse mapToOrderResponse(Order order) {
        OrderResponse orderResponse = new OrderResponse();
        BeanUtils.copyProperties(order, orderResponse, "shippingAddress", "billingAddress");
        orderResponse.setOrderCode(util.generateOrderCode(order.getId()));
        orderResponse.setBillingAddress(this.mapToBillingAddressResponse(order.getBillingAddress()));
        orderResponse.setShippingAddress(this.mapToShippingAddressResponse(order.getShippingAddress()));
        orderResponse.setEarnedPoints(order.getCredit());
        orderResponse.setCardNumber(order.getCard().getCardNumber());
        orderResponse.setOrderItems(
            order.getOrderDetails()
                .stream()
                .map(this::mapToOrderItemResponse)
                .collect(Collectors.toList())
        );
        orderResponse.setTotal(order.total());
        return orderResponse;
    }

    private OrderItemResponse mapToOrderItemResponse(OrderDetails orderDetails) {
        OrderItemResponse orderItemResponse = new OrderItemResponse();
        orderItemResponse.setPrice(orderDetails.getPrice());
        orderItemResponse.setQuantity(orderDetails.getQuantity());
        orderItemResponse.setProductName(orderDetails.getProduct().getName());
        orderItemResponse.setImage(orderDetails.getProduct().getImage());
        orderItemResponse.setProductId(orderDetails.getProduct().getId());
        orderItemResponse.setStatus(orderDetails.getStatus());
        orderItemResponse.setId(orderDetails.getId());
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

    public Order findById(long id) {
        return this.orderRepository.findById(id).isPresent()?this.orderRepository.findById(id).get():null;
    }

    @Override
    public Order cancelOrder(Order order, Long itemId) {
        OrderDetails orderDetail = order.getOrderDetails().stream().filter(oi -> oi.getId() == itemId).findFirst().orElseGet(null);
        if(orderDetail != null && orderDetail.getStatus().equals(OrderStatus.NEW)) {
            orderDetail.setStatus(OrderStatus.CANCELED);
            this.updatePointWhenCancelOrder(orderDetail);
        }

        return this.orderRepository.save(order);
    }

    @Override
    public String generateInvoiceOrder(Order order) {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode(TemplateMode.HTML);

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);

        OrderResponse orderResponse = mapToOrderResponse(order);

        orderResponse.setOrderItems(
                orderResponse.getOrderItems().stream().filter(oi -> oi.getStatus() != OrderStatus.CANCELED).collect(Collectors.toList())
        );

        Context context = new Context();
        context.setVariable("orderResponse", orderResponse);

        String cardNumber = order.getCard().getCardNumber();
        String lastFourDigits = cardNumber.substring(cardNumber.length() - 4);
        context.setVariable("lastFourDigitsCard", lastFourDigits);

        return templateEngine.process("/templates/views/buyer/invoiceOrder", context);
        //return templateEngine.process("views/buyer/invoiceOrder", context);
    }

    @Override
    public List<OrderItemResponse> getCreatedOrders(){
        List<OrderDetails> orderDetails = this.orderDetailRepository.getOrderDetailCreated(util.getCurrentUser().getId());
        return orderDetails
                .stream()
                .map(this::mapToOrderItemResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void updateStatusOrderBySeller(Long orderDetailId, OrderStatus status) {
        OrderDetails orderDetail =
                this.orderDetailRepository.findById(orderDetailId).isPresent()?
                        this.orderDetailRepository.findById(orderDetailId).get():null;

        if(orderDetail != null && orderDetail.getProduct().getSeller().equals(util.getCurrentUser())) {
            orderDetail.setStatus(status);
            this.orderDetailRepository.save(orderDetail);
            if (status.equals(OrderStatus.CANCELED))
                this.updatePointWhenCancelOrder(orderDetail);
        }
    }

    private void updatePointWhenCancelOrder(OrderDetails orderDetail){
        User buyer = orderDetail.getOrder().getBuyer();
        double orderItemPrice = orderDetail.getPrice();
        double buyerPoints = buyer.getPoints();
        buyer.setPoints(buyerPoints - orderItemPrice);
        this.userRepository.save(buyer);
    }
}
