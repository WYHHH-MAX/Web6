export interface OrderVO {
  id: number
  orderNo: string
  status: string
  totalAmount: number
  createTime: string
  updateTime: string
  userId?: number
  username?: string
  paymentMethod?: string
  phone?: string
  location?: string
  products: OrderProductVO[]
}

export interface OrderProductVO {
  id: number
  name: string
  image: string
  price: number
  quantity: number
  reviewed?: boolean
  refunded?: boolean
} 