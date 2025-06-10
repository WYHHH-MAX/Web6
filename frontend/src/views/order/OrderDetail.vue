<template>
  <div class="order-detail-page">
    <div class="content-wrapper">
      <div class="page-header">
        <a-button @click="goBack">
          <ArrowLeftOutlined />
          Return to the list of orders
        </a-button>
        <h1 class="page-title">Return to the order list, order details</h1>
      </div>
      
      <div v-if="loading" class="loading-container">
        <a-spin tip="Loading..."></a-spin>
      </div>
      
      <div v-else-if="!order" class="empty-container">
        <a-empty description="Order not found"></a-empty>
      </div>
      
      <div v-else class="order-detail">
        <!-- 订单基本信息 -->
        <div class="section">
          <h2 class="section-title">Order Information</h2>
          <div class="info-grid">
            <div class="info-item">
              <span class="label">Order number:</span>
              <span class="value">{{ order.orderNo }}</span>
            </div>
            <div class="info-item">
              <span class="label">Creation time:</span>
              <span class="value">{{ formatDate(order.createTime) }}</span>
            </div>
            <div class="info-item">
              <span class="label">Order Status:</span>
              <span class="value status" :class="getStatusClass(order.status)">
                {{ getStatusText(order.status) }}
              </span>
            </div>
            <div class="info-item">
              <span class="label">Payment Methods:</span>
              <span class="value">{{ order.paymentMethod || 'Not yet paid' }}</span>
            </div>
            <div class="info-item">
              <span class="label">Receiver Phone:</span>
              <span class="value">{{ order.phone || 'Not available' }}</span>
            </div>
            <div class="info-item">
              <span class="label">Shipping Address:</span>
              <span class="value">{{ order.location || 'Not available' }}</span>
            </div>
          </div>
        </div>
        
        <!-- 商品信息 -->
        <div class="section">
          <h2 class="section-title">Product information</h2>
          <div class="product-list">
            <div class="product-header">
              <div class="header-item product">Product information</div>
              <div class="header-item price">unit price</div>
              <div class="header-item quantity">quantity</div>
              <div class="header-item subtotal">subtotal</div>
            </div>
            <div v-for="product in order.products" :key="product.id" class="product-item">
              <div class="item-product">
                <img :src="getImageUrl(product.image)" :alt="product.name" class="product-image">
                <div class="product-info">
                  <div class="product-name">{{ product.name }}</div>
                  <div class="product-actions" v-if="parseInt(order.status) === ORDER_STATUS.COMPLETED">
                    <div class="action-buttons">
                      <div class="blue-box" v-if="!product.reviewed">
                        <a-button 
                          type="primary" 
                          class="full-width-btn"
                          @click="handleReviewProduct(product)"
                        >
                          Review
                        </a-button>
                      </div>
                      <div class="blue-box reviewed-box" v-else>
                        <check-circle-outlined /> Reviewed
                      </div>
                      <div class="red-box">
                        <a-button 
                          type="danger"
                          class="full-width-btn" 
                          @click="handleRefundProduct(product)"
                        >
                          Apply for refund
                        </a-button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div class="item-price">¥{{ product.price }}</div>
              <div class="item-quantity">{{ product.quantity }}</div>
              <div class="item-subtotal">¥{{ (product.price * product.quantity).toFixed(2) }}</div>
            </div>
          </div>
        </div>
        
        <!-- 金额信息 -->
        <div class="section">
          <h2 class="section-title">Amount information</h2>
          <div class="amount-info">
            <div class="amount-item">
              <span class="label">The total amount of the item:</span>
              <span class="value">¥{{ order.totalAmount }}</span>
            </div>
            <div class="amount-item">
              <span class="label">freight:</span>
              <span class="value">¥0.00</span>
            </div>
            <div class="amount-item total">
              <span class="label">Order Total:</span>
              <span class="value">¥{{ order.totalAmount }}</span>
            </div>
          </div>
        </div>
        
        <!-- 订单操作 -->
        <div class="actions">
          <template v-if="order.status === 0">
            <a-button type="primary" @click="handlePayOrder">Go and pay</a-button>
            <a-button @click="handleCancelOrder">Cancel the order</a-button>
          </template>
          
          <template v-if="order.status === 2">
            <a-button type="primary" @click="handleConfirmOrder">Confirm receipt</a-button>
          </template>
        </div>
      </div>
    </div>
    
    <!-- 支付模态框 -->
    <a-modal
      v-model:visible="paymentModalVisible"
      title="Order Payment"
      :footer="null"
      @cancel="cancelPayment"
    >
      <div class="payment-modal">
        <div class="payment-amount">
          <p>The amount of the order</p>
          <h2>¥{{ order?.totalAmount || 0 }}</h2>
        </div>
        
        <div class="payment-methods">
          <h3>Select a payment method</h3>
          <div class="method-options">
            <a-radio-group v-model:value="paymentMethod">
              <a-radio value="1">Alipay</a-radio>
              <a-radio value="2">WeChat Pay</a-radio>
              <a-radio value="3">Bank cards</a-radio>
              <a-radio value="4">cash on delivery</a-radio>
            </a-radio-group>
          </div>
        </div>
        
        <div class="payment-actions">
          <a-button @click="cancelPayment">cancel</a-button>
          <a-button type="primary" @click="completePayment" :loading="paymentLoading">
            Confirm the payment
          </a-button>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { ArrowLeftOutlined, ExclamationCircleOutlined, CheckCircleOutlined } from '@ant-design/icons-vue'
import { getOrders, payOrder, cancelOrder, confirmOrder, refundProductItem } from '@/api/order'
import { submitReview, hasReviewed } from '@/api/review'
import { getImageUrl } from '@/utils/imageUtil'

const route = useRoute()
const router = useRouter()
const orderId = ref(parseInt(route.params.id as string))
const order = ref<any>(null)
const loading = ref(true)

// 支付相关
const paymentModalVisible = ref(false)
const paymentMethod = ref('1')  // 默认使用支付宝
const paymentLoading = ref(false)

// 订单状态
const ORDER_STATUS = {
  PENDING_PAYMENT: 0,  // 待付款
  PENDING_SHIPMENT: 1, // 待发货
  PENDING_RECEIPT: 2,  // 待收货
  COMPLETED: 3,        // 已完成
  CANCELLED: 4         // 已取消
}

// 加载订单详情
const loadOrderDetail = async () => {
  if (!orderId.value) {
    message.error('订单ID不存在')
    router.push('/orders')
    return
  }
  
  loading.value = true
  try {
    // 获取订单列表，筛选出当前订单
    const result = await getOrders({})
    
    // 处理不同的响应格式，获取订单数组
    let orderList: any[] = [];
    
    if (result.code === 200 && result.data) {
      if (result.data.records) {
        orderList = result.data.records;
      } else if (result.data.list) {
        orderList = result.data.list;
      } else if (Array.isArray(result.data)) {
        orderList = result.data;
      } else if (typeof result.data === 'object') {
        const data = result.data;
        if (data.records) {
          orderList = data.records;
        } else if (data.list) {
          orderList = data.list;
        }
      }
    }
    
    // 筛选订单详情
    const orderDetail = orderList.find((item: any) => item.id === orderId.value)
    
    if (orderDetail) {
      order.value = orderDetail
      
      // 检查商品评价状态
      if (parseInt(order.value.status) === ORDER_STATUS.COMPLETED && order.value.products) {
        await loadProductReviewStatus();
      }
    } else {
      message.error('订单不存在')
    }
  } catch (error: any) {
    console.error('获取订单详情失败:', {
      message: error.message,
      status: error.response?.status,
      data: error.response?.data,
      config: error.config,
      fullURL: error.config?.baseURL ? `${error.config.baseURL}${error.config.url}` : error.config?.url
    });
    message.error('获取订单详情失败')
  } finally {
    loading.value = false
  }
}

// 加载商品评价状态
const loadProductReviewStatus = async () => {
  if (!order.value || !order.value.products) return;
  
  for (const product of order.value.products) {
    try {
      const result = await hasReviewed(order.value.orderNo, product.id);
      if (result.code === 200) {
        product.reviewed = result.data;
      }
    } catch (error) {
      console.error(`检查商品评价状态失败: orderId=${order.value.orderNo}, productId=${product.id}`, error);
    }
  }
};

// 获取订单状态文本
const getStatusText = (status: number) => {
  switch (parseInt(status.toString())) {
    case ORDER_STATUS.PENDING_PAYMENT:
      return '待付款'
    case ORDER_STATUS.PENDING_SHIPMENT:
      return '待发货'
    case ORDER_STATUS.PENDING_RECEIPT:
      return '待收货'
    case ORDER_STATUS.COMPLETED:
      return '已完成'
    case ORDER_STATUS.CANCELLED:
      return '已取消'
    default:
      return '未知状态'
  }
}

// 获取订单状态样式类
const getStatusClass = (status: number) => {
  switch (parseInt(status.toString())) {
    case ORDER_STATUS.PENDING_PAYMENT:
      return 'status-pending-payment'
    case ORDER_STATUS.PENDING_SHIPMENT:
      return 'status-pending-shipment'
    case ORDER_STATUS.PENDING_RECEIPT:
      return 'status-pending-receipt'
    case ORDER_STATUS.COMPLETED:
      return 'status-completed'
    case ORDER_STATUS.CANCELLED:
      return 'status-cancelled'
    default:
      return ''
  }
}

// 格式化日期
const formatDate = (dateString: string) => {
  if (!dateString) return ''
  const date = new Date(dateString)
  return date.toLocaleString()
}

// 返回订单列表
const goBack = () => {
  router.push('/orders')
}

// 支付订单
const handlePayOrder = () => {
  if (!order.value) return
  paymentModalVisible.value = true
}

// 完成支付
const completePayment = async () => {
  if (!order.value) {
    message.error('订单不存在')
    return
  }
  
  paymentLoading.value = true
  try {
    // 确保订单号是字符串类型
    const orderNoStr = String(order.value.orderNo);
    console.log('准备支付订单, 订单号(字符串):', orderNoStr);
    
    await payOrder(orderNoStr, paymentMethod.value)
    message.success('支付成功')
    
    // 关闭模态框
    paymentModalVisible.value = false
    
    // 跳转到个人中心页面
    router.push('/profile')
  } catch (error) {
    console.error('支付失败:', error)
    message.error('支付失败')
  } finally {
    paymentLoading.value = false
  }
}

// 取消支付
const cancelPayment = () => {
  paymentModalVisible.value = false
}

// 取消订单
const handleCancelOrder = () => {
  if (!order.value) return
  
  Modal.confirm({
    title: '确认取消订单',
    icon: () => h(ExclamationCircleOutlined),
    content: '确定要取消这个订单吗？',
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      try {
        await cancelOrder(order.value.orderNo)
        message.success('订单已取消')
        loadOrderDetail()
      } catch (error) {
        console.error('取消订单失败:', error)
        message.error('取消订单失败')
      }
    }
  })
}

// 确认收货
const handleConfirmOrder = () => {
  if (!order.value) return
  
  Modal.confirm({
    title: '确认收货',
    icon: () => h(ExclamationCircleOutlined),
    content: '确认已收到商品吗？',
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      try {
        await confirmOrder(order.value.orderNo)
        message.success('已确认收货')
        loadOrderDetail()
      } catch (error) {
        // console.error('确认收货失败:', error)
        message.error('确认收货失败')
      }
    }
  })
}

// 处理商品级别的评价
const handleReviewProduct = (product: any) => {
  if (!order.value) return;
  
  router.push({
    path: '/review',
    query: {
      orderNo: order.value.orderNo,
      productId: product.id.toString()
    }
  });
};

// 处理商品级别的退款
const handleRefundProduct = (product: any) => {
  if (!order.value) return;
  
  Modal.confirm({
    title: 'Apply for Refund',
    icon: () => h(ExclamationCircleOutlined),
    content: `Are you sure you want to request a refund for ${product.name}?`,
    okText: 'Confirm',
    cancelText: 'Cancel',
    onOk: async () => {
      try {
        const result = await refundProductItem(order.value.orderNo, product.id);
        
        if (result.code === 200) {
          message.success('Refund request submitted successfully');
          loadOrderDetail(); // 重新加载订单详情
        } else {
          throw new Error(result.msg || 'Failed to apply for refund');
        }
      } catch (error: any) {
        console.error('申请退款失败:', error);
        message.error(error.message || 'Failed to apply for refund');
      }
    }
  });
};

onMounted(() => {
  loadOrderDetail()
})
</script>

<style scoped>
.order-detail-page {
  padding: 24px;
}

.content-wrapper {
  background: #fff;
  padding: 24px;
  border-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.page-header {
  display: flex;
  align-items: center;
  margin-bottom: 24px;
}

.page-title {
  margin: 0 0 0 16px;
  font-weight: 500;
  color: #333;
}

.loading-container,
.empty-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
}

.section {
  margin-bottom: 32px;
}

.section-title {
  font-size: 18px;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.info-item {
  display: flex;
}

.label {
  color: #666;
  width: 100px;
}

.value {
  color: #333;
  font-weight: 500;
}

.status {
  font-weight: bold;
}

.status-pending-payment {
  color: #faad14;
}

.status-pending-shipment,
.status-pending-receipt {
  color: #1890ff;
}

.status-completed {
  color: #52c41a;
}

.status-cancelled {
  color: #d9d9d9;
}

.product-list {
  margin-bottom: 24px;
}

.product-header {
  display: flex;
  background-color: #f5f5f5;
  padding: 12px 16px;
  border-radius: 4px;
  margin-bottom: 16px;
  font-weight: 500;
}

.header-item {
  text-align: center;
}

.header-item.product {
  flex: 1;
  text-align: left;
}

.header-item.price,
.header-item.quantity,
.header-item.subtotal {
  flex: 0 0 120px;
}

.product-item {
  display: flex;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.item-product {
  flex: 1;
  display: flex;
  align-items: center;
}

.product-image {
  width: 60px;
  height: 60px;
  object-fit: cover;
  margin-right: 16px;
  border-radius: 4px;
}

.product-info {
  flex: 1;
}

.product-name {
  color: #333;
}

.item-price,
.item-quantity,
.item-subtotal {
  flex: 0 0 120px;
  text-align: center;
}

.item-subtotal {
  font-weight: 500;
  color: #ff4d4f;
}

.amount-info {
  background-color: #f5f5f5;
  padding: 16px;
  border-radius: 4px;
}

.amount-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.amount-item.total {
  padding-top: 8px;
  border-top: 1px dashed #d9d9d9;
  margin-top: 8px;
}

.amount-item.total .value {
  color: #ff4d4f;
  font-size: 18px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
}

/* 支付模态框样式 */
.payment-modal {
  padding: 16px;
}

.payment-amount {
  text-align: center;
  margin-bottom: 24px;
}

.payment-amount p {
  color: #666;
  margin-bottom: 8px;
}

.payment-amount h2 {
  font-size: 24px;
  color: #ff4d4f;
}

.payment-methods {
  margin-bottom: 24px;
}

.payment-methods h3 {
  margin-bottom: 16px;
}

.method-options {
  padding: 16px;
  background-color: #f5f5f5;
  border-radius: 4px;
}

.payment-actions {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
}

.product-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.action-buttons {
  display: flex;
  gap: 8px;
}

.blue-box {
  background-color: #e6f7ff;
  border: 1px solid #91d5ff;
  border-radius: 4px;
  padding: 4px 8px;
}

.blue-box.full-width-btn {
  width: 100%;
}

.blue-box.reviewed-box {
  background-color: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 4px;
  padding: 4px 8px;
}

.red-box {
  background-color: #fff1f0;
  border: 1px solid #ffa39e;
  border-radius: 4px;
  padding: 4px 8px;
}

.red-box.full-width-btn {
  width: 100%;
}
</style> 