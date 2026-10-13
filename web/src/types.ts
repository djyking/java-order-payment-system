export type Id = string;
export type Cents = number | string;
export interface User {
  userId: Id;
  username: string;
  role: 'USER' | 'ADMIN';
}
export interface Session extends User {
  accessToken: string;
}
export interface Product {
  skuId: Id;
  spuId: Id;
  spuName: string;
  skuName: string;
  priceCent: Cents;
  onShelf: boolean;
}
export interface Line {
  skuId: Id;
  skuName: string;
  priceCent: Cents;
  quantity: number;
  amountCent: Cents;
}
export interface Settlement {
  items: Line[];
  totalAmountCent: Cents;
}
export interface Order extends Settlement {
  orderNo: string;
  userId: Id;
  status: string;
  payStatus: string;
  createTime: string;
}
export interface AdminOrder {
  order_no: string;
  user_id: Id;
  status: string;
  pay_status: string;
  total_amount_cent: Cents;
  first_item_name: string;
  item_count: number;
  create_time: string;
}
export interface Inventory {
  skuId: Id;
  total: number;
  available: number;
  reserved: number;
  sold: number;
}
export interface PaymentMethod {
  code: string;
  name: string;
  available: boolean;
  reason?: string;
  actionType: string;
}
export interface Checkout {
  payOrderNo: string;
  status: string;
  methodCode: string;
  actionType: string;
  payUrl?: string;
  qrCode?: string;
  expiresAt?: string;
}
