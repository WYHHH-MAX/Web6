<template>
  <div class="order-list">
    <a-list
      :data-source="orders"
      :loading="loading"
      :pagination="pagination"
    >
      <template #renderItem="{ item }">
        <a-list-item>
          <a-card :title="'Order number:' + item.orderNo" style="width: 100%">
            <template #extra>
              <a-tag :color="getStatusColor(item.status)">
                {{ getStatusText(item.status) }}
              </a-tag>
            </template>
            
            <!-- 商品列表 -->
            <div class="order-items">
              <div v-for="product in item.products" :key="product.id" class="order-item">
                <img :src="getProductImageUrl(product.image)" :alt="product.name" class="product-image" />
                <div class="product-info">
                  <div class="product-name">{{ product.name }}</div>
                  <div class="product-price">¥{{ product.price.toFixed(2) }} × {{ product.quantity }}</div>
                  
                  <!-- 添加商品级别按钮 -->
                  <div class="product-actions" v-if="parseInt(item.status) === 3">
                    <div class="action-buttons">
                      <a-button 
                        v-if="!product.reviewed && !product.refunded" 
                        type="primary" 
                        size="small" 
                        @click="handleReviewProduct(item, product)"
                      >
                        Review
                      </a-button>
                      <span v-if="product.reviewed" class="reviewed-tag">
                        <check-circle-outlined /> Reviewed
                      </span>
                      <span v-if="product.refunded" class="refunded-tag">
                        <close-circle-outlined /> Refunded
                      </span>
                      <a-button 
                        v-if="!product.refunded"
                        type="danger"
                        size="small" 
                        @click="handleRefundProduct(item, product)"
                      >
                        Apply for refund
                      </a-button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            
            <!-- 订单信息 -->
            <div class="order-info">
              <div class="order-total">
                total:<span class="price">¥{{ item.totalAmount.toFixed(2) }}</span>
              </div>
              <div class="order-actions">
                <a-button
                  v-if="parseInt(item.status) === 0"
                  type="primary"
                  @click="handlePay(item)"
                >
                  Pay immediately
                </a-button>
                <a-button
                  v-if="parseInt(item.status) === 0"
                  @click="handleCancel(item)"
                >
                  Cancel the order
                </a-button>
                <a-button
                  v-if="parseInt(item.status) === 1"
                  @click="handleCancel(item)"
                >
                  Cancel the order
                </a-button>
                <a-button
                  v-if="parseInt(item.status) === 2"
                  type="primary"
                  @click="handleConfirm(item)"
                >
                  Confirm receipt
                </a-button>
              </div>
            </div>
          </a-card>
        </a-list-item>
      </template>
    </a-list>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted, watch, computed } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { getOrders, payOrder, cancelOrder, confirmOrder, refundOrder, refundProductItem } from '@/api/order'
import { submitReview, hasReviewed } from '@/api/review'
import { getImageUrl } from '@/utils/imageUtil'
import { useRouter } from 'vue-router'
import { ExclamationCircleOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons-vue'
import { h } from 'vue'

const props = defineProps<{
  status: string | null
}>()

// 添加调试日志，监控status属性
// console.log('OrderList初始化，接收到的status参数:', props.status, typeof props.status);

interface OrderProduct {
  id: number
  name: string
  image: string
  price: number
  quantity: number
  reviewed?: boolean
  refunded?: boolean
}

interface Order {
  id: number
  orderNo: string
  status: string
  totalAmount: number
  products: OrderProduct[]
}

const orders = ref<Order[]>([])
const loading = ref(false)
const total = ref(0)
const currentPage = ref(1)

// 添加加载标志，确保组件能正确加载数据
const isLoaded = ref(false)

const router = useRouter()

// 添加分页配置
const pageSize = ref(5); // 每页显示5条订单
const isLoading = ref(false); // 防止重复加载
const loadingMore = ref(false); // 加载更多状态

// 分页配置对象
const pagination = computed(() => ({
  current: currentPage.value,
  pageSize: pageSize.value,
  total: total.value,
  onChange: (page: number) => {
    currentPage.value = page;
    loadOrders();
  },
  showTotal: (total: number) => `Total ${total} items`,
  showSizeChanger: true,
  pageSizeOptions: ['5', '10', '20'],
  onShowSizeChange: (_current: number, size: number) => {
    pageSize.value = size;
    currentPage.value = 1;
    loadOrders();
  }
}));

// 加载商品评价状态
const loadProductReviewStatus = async () => {
  try {
    // 只检查已完成订单
    const completedOrders = orders.value.filter(order => parseInt(order.status) === 3);
    
    if (completedOrders.length === 0) {
      console.log('没有已完成订单需要检查评价状态');
      return;
    }
    
    console.log('已完成订单数量:', completedOrders.length);
    
    // 统计已评价商品数量
    let reviewedProductsCount = 0;
    completedOrders.forEach(order => {
      order.products.forEach(product => {
        if (product.reviewed) {
          reviewedProductsCount++;
        }
      });
    });
    
    console.log(`已评价商品数量: ${reviewedProductsCount}`);
    
  } catch (error) {
    console.error('处理评价状态失败:', error);
  }
};

const loadOrders = async () => {
  try {
    isLoading.value = true;
    loading.value = true;
    
    // 构建请求参数
    const params: any = { 
      page: currentPage.value || 1, 
      size: pageSize.value 
    };
    
    let res;
    
    // 处理特殊状态 - 部分退款订单
    if (props.status === 'refund-items') {
      params.hasRefundedItems = true;
      console.log('正在加载包含退款商品的订单');
      res = await getOrders(params);
    }
    // 处理退款状态 - 直接通过status=5标识
    else if (props.status === '5') {
      console.log('正在加载已退款订单');
      params.status = '5'; // 直接使用status参数
      res = await getOrders(params);
    }
    // 处理常规状态
    else if (props.status !== null && props.status !== undefined && props.status !== '') {
      params.status = props.status;
      console.log(`正在按状态 [${props.status}] (类型: ${typeof props.status}) 加载订单`);
      res = await getOrders(params);
    } else {
      console.log('加载所有状态的订单');
      res = await getOrders(params);
    }
    
    console.log('获取订单API响应:', res);
    
    // 处理 ApiResponse 格式的响应
    if (res.code === 200 && res.data) {
      console.log('解析API响应数据:', JSON.stringify(res.data));
      const responseData = res.data;
      
      // 处理 PageResult 格式的数据
      if (responseData.records && Array.isArray(responseData.records)) {
        orders.value = responseData.records;
        console.log('从 records 字段加载订单数据，数量:', orders.value.length);
        total.value = responseData.total || 0;
        console.log('设置总条数为:', total.value);
      } else if (responseData.list && Array.isArray(responseData.list)) {
        orders.value = responseData.list;
        console.log('从 list 字段加载订单数据，数量:', orders.value.length);
        total.value = responseData.total || 0;
        console.log('设置总条数为:', total.value);
      } else if (Array.isArray(responseData)) {
        orders.value = responseData;
        console.log('直接从 data 字段加载订单数组，数量:', orders.value.length);
        total.value = responseData.length || 0;
        console.log('设置总条数为:', total.value);
      } else {
        console.error('订单数据格式异常:', responseData);
        // 尝试推测可能的数据结构
        if (typeof responseData === 'object' && responseData !== null) {
          // 类型断言为any以处理未知的响应结构
          const anyData = responseData as any;
          
          if (anyData.content && Array.isArray(anyData.content)) {
            // Spring Data JPA 风格的分页
            orders.value = anyData.content;
            total.value = anyData.totalElements || 0;
            console.log('从Spring Data格式加载订单数据，数量:', orders.value.length);
          } else {
            // 试图找到对象中的任何数组属性
            const arrayProp = Object.keys(anyData).find(key => Array.isArray(anyData[key]));
            if (arrayProp) {
              orders.value = anyData[arrayProp];
              total.value = orders.value.length;
              console.log(`从自定义属性 ${arrayProp} 加载订单数据，数量:`, orders.value.length);
            } else {
              orders.value = [];
              total.value = 0;
            }
          }
        } else {
        orders.value = [];
          total.value = 0;
        }
      }
      
      // 防止订单数据错误导致分页不正确
      if (total.value === 0 && orders.value.length > 0) {
        console.log('自动修正total值为实际订单数量');
        total.value = orders.value.length;
      }
      
      // 处理部分退款订单的情况 - 如果是查询退款商品的订单，筛选包含已退款商品的订单
      if (props.status === 'refund-items') {
        orders.value = orders.value.filter(order => {
          return order.products.some(product => product.refunded === true);
        });
        console.log(`筛选后包含退款商品的订单数量: ${orders.value.length}`);
        total.value = orders.value.length;
      }
      
      // 只有在加载完订单且是"已完成"状态时才检查评价状态
      if (props.status === '3' && orders.value.length > 0) {
        await loadProductReviewStatus();
      } else {
        console.log('跳过评价状态检查：非已完成状态或无订单');
      }
      
      isLoaded.value = true;
    } else {
      console.error('API 返回错误:', res);
      message.error(res.msg || '加载订单失败');
      orders.value = [];
      total.value = 0;
    }
  } catch (error: any) {
    console.error('获取订单列表失败:', error);
    if (error.response?.status === 404) {
      message.error('订单API路径不存在，请检查后端服务是否正常运行');
    } else {
      message.error(typeof error === 'string' ? error : '获取订单列表失败');
    }
    orders.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
    isLoading.value = false;
    loadingMore.value = false;
  }
}

const getStatusColor = (status: string) => {
  // 将字符串转为数字
  const statusNum = parseInt(status)
  
  // 根据数字状态码返回颜色
  switch(statusNum) {
    case 0: return 'orange' // 待付款
    case 1: return 'blue'   // 待发货
    case 2: return 'cyan'   // 待收货
    case 3: return 'green'  // 已完成
    case 4: return 'red'    // 已取消
    case 5: return 'purple' // 已退款，添加
    default: return 'default'
  }
}

const getStatusText = (status: string) => {
  // 将字符串转为数字
  const statusNum = parseInt(status)
  
  // 根据数字状态码返回文本
  switch(statusNum) {
    case 0: return 'Pending payment'
    case 1: return 'To be shipped'
    case 2: return 'To be received'
    case 3: return 'Done'
    case 4: return 'Canceled'
    case 5: return 'Refunded'  // 添加退款状态
    default: return status
  }
}

const handlePay = async (order: Order) => {
  try {
    const response = await payOrder(order.orderNo);
    console.log('支付响应:', response);
    
    if (response && response.code === 200) {
      message.success('The payment was successful');
      loadOrders();
    } else {
      message.error((response && response.msg) || 'The payment failed');
    }
  } catch (error) {
    console.error('支付失败:', error);
    message.error('The payment failed');
  }
}

const handleCancel = async (order: Order) => {
  try {
    const response = await cancelOrder(order.orderNo);
    console.log('取消订单响应:', response);
    
    if (response && response.code === 200) {
      message.success('The cancellation of the order is successful');
      loadOrders();
    } else {
      message.error((response && response.msg) || 'Failed to cancel the order');
    }
  } catch (error) {
    console.error('取消订单失败:', error);
    message.error('Failed to cancel the order');
  }
}

const handleConfirm = async (order: Order) => {
  try {
    const response = await confirmOrder(order.orderNo);
    console.log('确认收货响应:', response);
    
    if (response && response.code === 200) {
      message.success('Confirm that the receipt is successful');
      loadOrders();
    } else {
      message.error((response && response.msg) || 'Failed to confirm receipt');
    }
  } catch (error) {
    console.error('确认收货失败:', error);
    message.error('Failed to confirm receipt');
  }
}

// 处理商品级评价
const handleReviewProduct = (order: Order, product: OrderProduct) => {
  console.log('准备评价商品:', product.id, '订单号:', order.orderNo);
  
  // 记录跳转前的路径状态，以便返回时能够回到当前页和状态
  const returnPath = {
    path: router.currentRoute.value.path,
    query: {
      ...router.currentRoute.value.query,
      returnFromReview: 'true', // 标记是从评价页返回
      reviewedProductId: product.id.toString(), // 记录评价的商品ID
      reviewedOrderNo: order.orderNo // 记录评价的订单号
    }
  };
  
  // 跳转到评价页面，传递订单和商品信息
  router.push({
    path: '/review',
    query: {
      orderNo: order.orderNo,
      productId: product.id.toString(),
      returnPath: encodeURIComponent(JSON.stringify(returnPath))
    }
  });
}

// 处理商品级退款
const handleRefundProduct = (order: Order, product: any) => {
  Modal.confirm({
    title: 'Apply for Refund',
    icon: () => h(ExclamationCircleOutlined),
    content: `Are you sure you want to request a refund for ${product.name}?`,
    okText: 'Confirm',
    cancelText: 'Cancel',
    onOk: async () => {
      try {
        const result = await refundProductItem(order.orderNo, product.id);
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

// 监听status变化，重新加载订单
watch(() => props.status, (newStatus) => {
  console.log('The order status parameter changes:', newStatus);
  loadOrders();
}, { immediate: true }); // 添加immediate: true确保组件初始化时就加载数据

// 初始化时也加载一次，确保status为null时也能加载数据
onMounted(() => {
  console.log('OrderList组件已挂载，status=', props.status, '类型:', typeof props.status);
  
  // 检查是否从评价页面返回
  const returnFromReview = router.currentRoute.value.query.returnFromReview;
  if (returnFromReview === 'true') {
    console.log('从评价页面返回，正在刷新订单状态');
    
    // 获取被评价的商品ID和订单号
    const reviewedProductId = router.currentRoute.value.query.reviewedProductId;
    const reviewedOrderNo = router.currentRoute.value.query.reviewedOrderNo;
    console.log(`评价的商品ID: ${reviewedProductId}, 订单号: ${reviewedOrderNo}`);
    
    // 清除返回标记
    const currentQuery = { ...router.currentRoute.value.query };
    delete currentQuery.returnFromReview;
    delete currentQuery.reviewedProductId;
    delete currentQuery.reviewedOrderNo;
    router.replace({ query: currentQuery }).catch(() => {});
    
    // 加载订单并检查评价状态
    loadOrders();
  } else if (!isLoaded.value) {
    loadOrders();
  }
});

const getProductImageUrl = (url: string) => {
  return getImageUrl(url);
}
</script>

<style scoped>
.order-list {
  margin-top: 16px;
}

.order-items {
  margin: 16px 0;
}

.order-item {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.product-image {
  width: 80px;
  height: 80px;
  object-fit: cover;
  margin-right: 16px;
}

.product-info {
  flex: 1;
}

.product-name {
  margin-bottom: 8px;
}

.product-price {
  color: #999;
}

.order-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.order-total {
  font-size: 16px;
}

.price {
  color: #f5222d;
  font-weight: bold;
}

.order-actions {
  display: flex;
  gap: 8px;
}

.product-actions {
  margin-top: 8px;
}

.action-buttons {
  display: flex;
  gap: 8px;
  align-items: center;
}

.reviewed-tag {
  color: #52c41a;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  font-weight: 500;
  background-color: #f6ffed;
  padding: 4px 10px;
  border-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  border: 1px solid #b7eb8f;
  transition: all 0.3s ease;
}

.reviewed-tag:hover {
  background-color: #eaffe8;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.refunded-tag {
  color: #f5222d;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  font-weight: 500;
  background-color: #fff1f0;
  padding: 4px 10px;
  border-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  border: 1px solid #ffa39e;
  transition: all 0.3s ease;
}

.refunded-tag:hover {
  background-color: #fff6f6;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

/* 移除不需要的样式 */
.blue-box, .red-box, .action-btn {
  display: none;
}
</style> 