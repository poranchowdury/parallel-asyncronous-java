package com.learnjava.completablefuture;

import com.learnjava.domain.*;
import com.learnjava.service.InventoryService;
import com.learnjava.service.ProductInfoService;
import com.learnjava.service.ReviewService;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.learnjava.util.CommonUtil.*;
import static com.learnjava.util.LoggerUtil.log;

public class ProductServiceUseCompletableFutureException {
    private final ProductInfoService productInfoService;
    private final ReviewService reviewService;
    private  InventoryService inventoryService;

    public ProductServiceUseCompletableFutureException(ProductInfoService productInfoService, ReviewService reviewService) {
        this.productInfoService = productInfoService;
        this.reviewService = reviewService;
    }

    public ProductServiceUseCompletableFutureException(ProductInfoService productInfoService, ReviewService reviewService, InventoryService inventoryService) {
        this.productInfoService = productInfoService;
        this.reviewService = reviewService;
        this.inventoryService = inventoryService;
    }

    public Product retrieveProductDetails(String productId) {
        stopWatchReset();
        startTimer();
        CompletableFuture<ProductInfo> productInfoCompletableFuture = CompletableFuture
                .supplyAsync(() -> productInfoService.retrieveProductInfo(productId));
        CompletableFuture<Review> reviewCompletableFuture = CompletableFuture
                .supplyAsync(() -> reviewService.retrieveReviews(productId));

        Product product = productInfoCompletableFuture.
                thenCombine(reviewCompletableFuture, ((productInfo, review) -> new Product(productId, productInfo, review)))
                .join();

        timeTaken();
        log("Total Time Taken : " + stopWatch.getTime());
        return  product;
    }
    public Product retrieveProductDetailsWithInventory(String productId) {
        stopWatchReset();
        startTimer();
        CompletableFuture<ProductInfo> productInfoCompletableFuture = CompletableFuture.supplyAsync(() -> productInfoService.retrieveProductInfo(productId))
                .thenApply(productInfo -> {
                    productInfo.setProductOptions(updateInventory(productInfo));
                    return productInfo;
                });
        CompletableFuture<Review> reviewCompletableFuture = CompletableFuture
                .supplyAsync(() -> reviewService.retrieveReviews(productId));

        Product product = productInfoCompletableFuture.
                thenCombine(reviewCompletableFuture, ((productInfo, review) -> new Product(productId, productInfo, review)))
                .join();
        timeTaken();

        return  product;
    }
    public Product retrieveProductDetailsWithInventoryApproachCF2(String productId) {
        stopWatchReset();
        startTimer();
        CompletableFuture<ProductInfo> productInfoCompletableFuture = CompletableFuture.supplyAsync(() -> productInfoService.retrieveProductInfo(productId))
                .thenApply(productInfo -> {
                    productInfo.setProductOptions(updateInventoryApproachCF(productInfo));
                    return productInfo;
                });
        CompletableFuture<Review> reviewCompletableFuture = CompletableFuture
                .supplyAsync(() -> reviewService.retrieveReviews(productId))
                .exceptionally((e)-> {
                    log("Handle exception in review service : "+ e.getMessage());
                    return Review.builder()
                            .noOfReviews(0)
                            .overallRating(0.0)
                            .build();
                });

        Product product = productInfoCompletableFuture.
                thenCombine(reviewCompletableFuture, ((productInfo, review) -> new Product(productId, productInfo, review)))
                .whenComplete(((product1, ex) -> {
                    log("inside whenComplete : "+ product1 + "and the exception is "+ex.getMessage());
                }))
                .join();
        timeTaken();

        return  product;
    }
    public Product retrieveProductDetailsWithInventoryApproachCF3WithInventoryException(String productId) {
        stopWatchReset();
        startTimer();
        CompletableFuture<ProductInfo> productInfoCompletableFuture = CompletableFuture.supplyAsync(() -> productInfoService.retrieveProductInfo(productId))
                .thenApply(productInfo -> {
                    productInfo.setProductOptions(updateInventoryToProductOption_approach3(productInfo));
                    return productInfo;
                });
        CompletableFuture<Review> reviewCompletableFuture = CompletableFuture
                .supplyAsync(() -> reviewService.retrieveReviews(productId))
                .exceptionally((e)-> {
                    log("Handle exception in review service : "+ e.getMessage());
                    return Review.builder()
                            .noOfReviews(0)
                            .overallRating(0.0)
                            .build();
                });

        Product product = productInfoCompletableFuture.
                thenCombine(reviewCompletableFuture, ((productInfo, review) -> new Product(productId, productInfo, review))).join();
        timeTaken();

        return  product;
    }

    private List<ProductOption> updateInventory(ProductInfo productInfo) {
        return productInfo.getProductOptions()
                .stream().peek(productOption -> {
                    Inventory inventory = inventoryService.retrieveInventory(productOption);
                    productOption.setInventory(inventory);
                })
                .collect(Collectors.toList());
    }
    private List<ProductOption> updateInventoryApproachCFWithInvetoryEXception(ProductInfo productInfo) {
        List<CompletableFuture<ProductOption>> productOptionList = productInfo.getProductOptions()
                .stream().map(productOption -> inventoryService.retrieveInventory_CF(productOption)
                        .exceptionally((ex)->{
                            log("Exception in Inventory Service : " + ex.getMessage());
                            return Inventory.builder()
                                    .count(1).build();
                        })
                        .thenApply(inventory -> {
                            productOption.setInventory(inventory);
                            return productOption;
                        }))
                .collect(Collectors.toList());
        CompletableFuture<Void> cfAllOf = CompletableFuture.allOf(productOptionList.toArray(new CompletableFuture[0]));
        return cfAllOf
                .thenApply(v-> productOptionList.stream().map(CompletableFuture::join)
                        .collect(Collectors.toList()))
                .join();
    }
    private List<ProductOption> updateInventoryToProductOption_approach3(ProductInfo productInfo) {

        List<CompletableFuture<ProductOption>> productOptionList = productInfo.getProductOptions()
                .stream()
                .map(productOption ->
                        CompletableFuture.supplyAsync(() -> inventoryService.retrieveInventory(productOption))
                                .exceptionally((ex) -> {
                                    log("Exception in Inventory Service : " + ex.getMessage());
                                    return Inventory.builder()
                                            .count(1).build();
                                })
                                .thenApply((inventory -> {
                                    productOption.setInventory(inventory);
                                    return productOption;
                                })))
                .collect(Collectors.toList());

        CompletableFuture<Void> cfAllOf = CompletableFuture.allOf(productOptionList.toArray(new CompletableFuture[productOptionList.size()]));
        return cfAllOf
                .thenApply(v->{
                    return  productOptionList.stream().map(CompletableFuture::join)
                            .collect(Collectors.toList());
                })
                .join();

    }
    private List<ProductOption> updateInventoryApproachCF(ProductInfo productInfo) {
        List<CompletableFuture<ProductOption>> productOptionList = productInfo.getProductOptions()
                .stream().map(productOption -> CompletableFuture.supplyAsync(() -> inventoryService.retrieveInventory(productOption))
                        .thenApply(inventory -> {
                            productOption.setInventory(inventory);
                            return productOption;
                        }))
                .collect(Collectors.toList());
       return productOptionList.stream().map(CompletableFuture::join).collect(Collectors.toList());
    }

    public CompletableFuture<Product> retrieveProductDetailsApproach2(String productId) {
        stopWatch.reset();
        stopWatch.start();
        CompletableFuture<ProductInfo> productInfoCompletableFuture = CompletableFuture
                .supplyAsync(() -> productInfoService.retrieveProductInfo(productId));
        CompletableFuture<Review> reviewCompletableFuture = CompletableFuture
                .supplyAsync(() -> reviewService.retrieveReviews(productId));

        CompletableFuture<Product> product = productInfoCompletableFuture.
                thenCombine(reviewCompletableFuture, ((productInfo, review) -> new Product(productId, productInfo, review)));

        stopWatch.stop();
        log("Total Time Taken : " + stopWatch.getTime());
        return  product;
    }

    public static void main(String[] args) throws InterruptedException {

        ProductInfoService productInfoService = new ProductInfoService();
        ReviewService reviewService = new ReviewService();
        ProductServiceUseCompletableFutureException productService = new ProductServiceUseCompletableFutureException(productInfoService, reviewService);
        String productId = "ABC123";
        Product product = productService.retrieveProductDetails(productId);
        log("Product is " + product);

    }

    public class ReviewInfoRunable implements Runnable {
        private final String productId;
        private Review review;

        public ReviewInfoRunable(String productId) {
            this.productId = productId;
        }

        public Review getReview() {
            return review;
        }

        @Override
        public void run() {
            review = reviewService.retrieveReviews(this.productId);
        }
    }

    public class ProductInfoRunnable implements Runnable {
        private ProductInfo productInfo;
        private final String productId;

        public ProductInfoRunnable(String productId) {
            this.productId = productId;
        }

        @Override
        public void run() {
            productInfo = productInfoService.retrieveProductInfo(productId);
        }

        public ProductInfo getProductInfo() {
            return productInfo;
        }

    }
}
