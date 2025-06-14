<template>
  <div class="order-list-page">
    <div class="content-wrapper">
      <h1 class="page-title">My Orders</h1>
      
      <div class="filter-section">
        <a-tabs v-model:activeKey="activeTab" @change="handleTabChange">
          <a-tab-pane key="all" tab="All orders">
            <order-list :status="null" />
          </a-tab-pane>
          <a-tab-pane key="0" tab="Pending payment">
            <order-list status="0" />
          </a-tab-pane>
          <a-tab-pane key="1" tab="To be shipped">
            <order-list status="1" />
          </a-tab-pane>
          <a-tab-pane key="2" tab="To be received">
            <order-list status="2" />
          </a-tab-pane>
          <a-tab-pane key="3" tab="Done">
            <order-list status="3" />
          </a-tab-pane>
          <a-tab-pane key="4" tab="Cancelled">
            <order-list status="4" />
          </a-tab-pane>
          <a-tab-pane key="5" tab="Refunded">
            <order-list status="5" />
          </a-tab-pane>
        </a-tabs>
      </div>
      
      <div v-if="loading" class="loading-container">
        <a-spin tip="Loading..."></a-spin>
      </div>
      
      <div v-else-if="orders.length === 0" class="empty-container">
        <a-empty description="There are no orders yet"></a-empty>
      </div>
      
      <div v-else class="order-list">
        <div v-for="order in orders" :key="order.id" class="order-card">
          <div class="order-header">
            <div class="order-info">
              <span class="order-no">Order number:{{ order.orderNo }}</span>
              <span class="order-date">{{ formatDate(order.createTime) }}</span>
            </div>
            <div class="order-status" :class="getStatusClass(order.status)">
              {{ getStatusText(order.status) }}
            </div>
          </div>
          
          <div class="order-items">
            <div v-for="product in order.products" :key="product.id" class="order-item">
              <img :src="getImageUrl(product.image)" :alt="product.name" class="product-image">
              <div class="product-info">
                <div class="product-name">{{ product.name }}</div>
                <div class="product-price">¥{{ product.price }} × {{ product.quantity }}</div>
                <div class="product-actions" v-if="parseInt(order.status) === ORDER_STATUS.COMPLETED">
                  <div class="action-buttons">
                    <div class="blue-box" v-if="!product.reviewed">
                      <a-button 
                        type="primary" 
                        class="full-width-btn"
                        @click="handleReviewProduct(order, product)"
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
                        @click="handleRefundProduct(order, product)"
                      >
                        Apply for refund
                      </a-button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          
          <div class="order-footer">
            <div class="order-amount">
              <span>totally, {{ getTotalQuantity(order) }} units, total:</span>
              <span class="total-price">¥{{ order.totalAmount }}</span>
            </div>
            
            <div class="order-actions">
              <template v-if="parseInt(order.status) === ORDER_STATUS.PENDING_PAYMENT">
                <a-button type="primary" @click="handlePayOrder(order)">Go and pay</a-button>
                <a-button @click="handleCancelOrder(order)">Cancel the order</a-button>
              </template>
              
              <template v-if="parseInt(order.status) === ORDER_STATUS.PENDING_SHIPMENT">
                <a-button @click="handleCancelOrder(order)">Cancel the order</a-button>
              </template>
              
              <template v-if="parseInt(order.status) === ORDER_STATUS.PENDING_RECEIPT">
                <a-button type="primary" @click="handleConfirmOrder(order)">Confirm receipt</a-button>
              </template>
              
              <template v-if="parseInt(order.status) === ORDER_STATUS.COMPLETED">
                <a-button @click="handleViewOrderDetail(order)">Find out more</a-button>
              </template>
              
              <template v-if="parseInt(order.status) === ORDER_STATUS.CANCELLED || parseInt(order.status) === ORDER_STATUS.REFUNDED">
                <a-button @click="handleViewOrderDetail(order)">Find out more</a-button>
              </template>
            </div>
          </div>
        </div>
      </div>
      
      <div class="pagination-container" v-if="orders.length > 0">
        <a-pagination
          v-model:current="currentPage"
          :total="total"
          :pageSize="pageSize"
          show-quick-jumper
          @change="handlePageChange"
        />
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
          <h2>¥{{ currentOrderAmount }}</h2>
        </div>
        
        <div class="payment-methods">
          <h3>Select a payment method</h3>
          <div class="method-options">
            <a-radio-group v-model:value="paymentMethod">
              <a-radio value="wechat">WeChat Pay</a-radio>
              <a-radio value="alipay">Alipay</a-radio>
              <a-radio value="card">Bank cards</a-radio>
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
import { ref, reactive, computed, onMounted, h } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { getOrders, payOrder, cancelOrder, confirmOrder, refundOrder, refundProductItem } from '@/api/order'
import { submitReview, hasReviewed } from '@/api/review'
import type { PageResult } from '@/types/common'
import { getImageUrl } from '@/utils/imageUtil'
import { ExclamationCircleOutlined, CheckCircleOutlined } from '@ant-design/icons-vue'
import OrderList from '@/components/OrderList.vue'

const router = useRouter()
const route = useRoute()

// 订单状态
const ORDER_STATUS = {
  PENDING_PAYMENT: 0,  // 待付款
  PENDING_SHIPMENT: 1, // 待发货
  PENDING_RECEIPT: 2,  // 待收货
  COMPLETED: 3,        // 已完成
  CANCELLED: 4,        // 已取消
  REFUNDED: 5          // 已退款
}

// 列表数据
const orders = ref<any[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const activeTab = ref('all')

// 支付相关
const paymentModalVisible = ref(false)
const paymentMethod = ref('wechat')
const paymentLoading = ref(false)
const currentOrderNo = ref('')
const currentOrderAmount = ref(0)

// 加载订单列表
const loadOrders = async () => {
  loading.value = true
  console.log('开始加载订单列表, 参数:', {
    status: activeTab.value || null,
    page: currentPage.value,
    size: pageSize.value
  });
  
  try {
    // console.log('发送请求前，完整URL:', '/api/orders');
    const result = await getOrders({
      status: activeTab.value || null,
      page: currentPage.value,
      size: pageSize.value
    })
    
    // console.log('订单列表加载成功:', result);
    
    // 处理不同的响应格式
    if (result.code === 200 && result.data) {
      if (result.data.records) {
        // 原始格式，包含records字段
        orders.value = result.data.records;
        total.value = result.data.total || 0;
      } else if (result.data.list) {
        // 包含list字段的格式
        orders.value = result.data.list;
        total.value = result.data.total || 0;
      } else if (Array.isArray(result.data)) {
        // data直接是数组
        orders.value = result.data;
        total.value = result.data.length;
      } else {
        console.error('订单数据格式异常，data字段结构不符合预期:', result.data);
        orders.value = [];
        total.value = 0;
      }
      
      // 加载商品评价状态
      if (orders.value.length > 0) {
        await loadProductReviewStatus(orders.value);
      }
    } else {
      console.warn('API返回失败或数据为空:', result);
      orders.value = [];
      total.value = 0;
    }
    
    // 输出每个订单的状态，便于调试
    if (orders.value.length > 0) {
      console.log('订单状态详情:');
      orders.value.forEach((order, index) => {
        console.log(`订单 ${index+1} - orderNo: ${order.orderNo}, 状态值: ${order.status}, 类型: ${typeof order.status}`);
      });
    }
    
    console.log('处理后的订单列表数据:', {
      length: orders.value.length,
      total: total.value,
      sample: orders.value.length > 0 ? orders.value[0] : null
    });
  } catch (error: any) {
    console.error('获取订单列表失败:', {
      message: error.message,
      status: error.response?.status,
      data: error.response?.data,
      config: error.config,
      fullURL: error.config?.baseURL ? `${error.config.baseURL}${error.config.url}` : error.config?.url
    });
    
    if (error.response?.status === 404) {
      message.error('订单API路径不存在，请检查后端服务是否正常运行');
    } else {
      message.error(error.message || '获取订单列表失败');
    }
    
    orders.value = [];
    total.value = 0;
  } finally {
    loading.value = false
  }
}

// 加载订单中每个商品的评价状态
const loadProductReviewStatus = async (orders: any[]) => {
  try {
    // 收集所有需要检查的商品ID和订单号
    const checkList: {orderId: string, productId: number}[] = [];
    
    orders.forEach(order => {
      if (parseInt(order.status) === ORDER_STATUS.COMPLETED) {
        order.products.forEach((product: any) => {
          checkList.push({
            orderId: order.orderNo,
            productId: product.id
          });
        });
      }
    });
    
    // 如果没有需要检查的商品，直接返回
    if (checkList.length === 0) {
      return;
    }
    
    // 对每个商品检查是否已评价
    for (const item of checkList) {
      try {
        const result = await hasReviewed(item.orderId, item.productId);
        if (result.code === 200) {
          // 找到对应的订单和商品，设置reviewed属性
          const order = orders.find(o => o.orderNo === item.orderId);
          if (order) {
            const product = order.products.find((p: any) => p.id === item.productId);
            if (product) {
              product.reviewed = result.data;
            }
          }
        }
      } catch (error) {
        console.error(`检查商品评价状态失败: orderId=${item.orderId}, productId=${item.productId}`, error);
      }
    }
  } catch (error) {
    console.error('加载商品评价状态失败:', error);
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
    case ORDER_STATUS.REFUNDED:
      return '已退款'
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
    case ORDER_STATUS.REFUNDED:
      return 'status-refunded'
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

// 计算订单商品总数量
const getTotalQuantity = (order: any) => {
  return order.products.reduce((total: number, product: any) => total + product.quantity, 0)
}

// 切换状态标签
const handleTabChange = (key: string) => {
  currentPage.value = 1
  activeTab.value = key
  loadOrders()
}

// 翻页
const handlePageChange = (page: number) => {
  currentPage.value = page
  loadOrders()
}

// 支付订单
const handlePayOrder = (order: any) => {
  currentOrderNo.value = order.orderNo
  currentOrderAmount.value = order.totalAmount
  paymentModalVisible.value = true
}

// 完成支付
const completePayment = async () => {
  if (!currentOrderNo.value) {
    message.error('订单号不存在')
    return
  }
  
  paymentLoading.value = true
  try {
    await payOrder(currentOrderNo.value)
    message.success('支付成功')
    
    // 关闭模态框并刷新列表
    paymentModalVisible.value = false
    loadOrders()
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
const handleCancelOrder = (order: any) => {
  Modal.confirm({
    title: '确认取消订单',
    icon: () => h(ExclamationCircleOutlined),
    content: '确定要取消这个订单吗？',
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      try {
        await cancelOrder(order.orderNo)
        message.success('订单已取消')
        loadOrders()
      } catch (error) {
        console.error('取消订单失败:', error)
        message.error('取消订单失败')
      }
    }
  })
}

// 确认收货
const handleConfirmOrder = (order: any) => {
  Modal.confirm({
    title: '确认收货',
    icon: () => h(ExclamationCircleOutlined),
    content: '确认已收到商品吗？',
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      try {
        await confirmOrder(order.orderNo)
        message.success('已确认收货')
        loadOrders()
      } catch (error) {
        console.error('确认收货失败:', error)
        message.error('确认收货失败')
      }
    }
  })
}

// 查看订单详情
const handleViewOrderDetail = (order: any) => {
  router.push(`/order/${order.id}`)
}

// 申请退款
const handleRefundOrder = (order: any) => {
  Modal.confirm({
    title: '确认申请退款',
    icon: () => h(ExclamationCircleOutlined),
    content: '确定要申请退款吗？',
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      try {
        await refundOrder(order.orderNo)
        message.success('退款申请成功')
        loadOrders()
      } catch (error) {
        console.error('申请退款失败:', error)
        message.error('申请退款失败')
      }
    }
  })
}

// 添加商品级别的评价函数
const handleReviewProduct = (order: any, product: any) => {
  router.push({
    path: '/review',
    query: {
      orderNo: order.orderNo,
      productId: product.id.toString()
    }
  });
};

// 添加商品级别的退款函数
const handleRefundProduct = (order: any, product: any) => {
  Modal.confirm({
    title: 'Apply for Refund',
    icon: () => h(ExclamationCircleOutlined),
    content: `Are you sure you want to request a refund for ${product.name}?`,
    okText: 'Confirm',
    cancelText: 'Cancel',
    onOk: async () => {
      try {
        console.log('发送退款请求...');
        // 使用单个商品退款API
        const result = await refundProductItem(order.orderNo, product.id);
        console.log('退款请求结果:', result);
        
        if (result.code === 200) {
          message.success('Refund request submitted successfully');
          loadOrders(); // 刷新订单列表
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
  loadOrders()
})
</script>

<style scoped>
.order-list-page {
  padding: 24px;
}

.content-wrapper {
  background: #fff;
  padding: 24px;
  border-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.page-title {
  margin-bottom: 24px;
  font-weight: 500;
  color: #333;
}

.filter-section {
  margin-bottom: 24px;
}

.loading-container,
.empty-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
}

.order-list {
  margin-bottom: 24px;
}

.order-card {
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  margin-bottom: 16px;
  overflow: hidden;
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background-color: #f5f5f5;
}

.order-info {
  display: flex;
  gap: 24px;
}

.order-no {
  font-weight: 500;
}

.order-date {
  color: #666;
}

.order-status {
  font-weight: 500;
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

.status-refunded {
  color: #722ed1;
}

.order-items {
  padding: 16px;
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.order-item {
  display: flex;
  align-items: center;
  width: calc(33.33% - 12px);
  min-width: 240px;
}

.product-image {
  width: 60px;
  height: 60px;
  object-fit: cover;
  margin-right: 12px;
  border-radius: 4px;
}

.product-info {
  flex: 1;
}

.product-name {
  color: #333;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-price {
  color: #666;
  font-size: 12px;
}

.product-actions {
  margin-top: 8px;
}

.action-buttons {
  display: flex;
  gap: 8px;
}

.blue-box {
  border: 2px solid #1890ff;
  border-radius: 4px;
  height: 32px;
  min-width: 100px;
  background-color: #fff;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.blue-box.reviewed-box {
  border: 2px solid #52c41a;
  background-color: #f6ffed;
  color: #52c41a;
  font-size: 14px;
}

.red-box {
  border: 2px solid #ff4d4f;
  border-radius: 4px;
  height: 32px;
  min-width: 100px;
  background-color: #fff;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.full-width-btn {
  width: 100%;
  height: 100%;
  border: none;
  box-shadow: none;
  display: flex;
  justify-content: center;
  align-items: center;
}

.blue-box .full-width-btn {
  background-color: #fff;
  color: #1890ff;
}

.red-box .full-width-btn {
  background-color: #fff;
  color: #ff4d4f;
}

.blue-box .full-width-btn:hover {
  background-color: #e6f7ff;
}

.red-box .full-width-btn:hover {
  background-color: #fff1f0;
}

.order-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
  background-color: #fafafa;
}

.order-amount {
  color: #666;
}

.total-price {
  font-weight: 500;
  color: #ff4d4f;
  margin-left: 8px;
}

.order-actions {
  display: flex;
  gap: 8px;
}

.pagination-container {
  margin-top: 24px;
  text-align: right;
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
</style> 