import { useState } from "react";
import { createTransaction } from "../services/transactionService";

const DepositMoney = ({ userId }) => {
    const [amount, setAmount] = useState("");
    const [paymentMethod, setPaymentMethod] = useState("PAYPAL");

    const handleDeposit = async () => {
        if (!amount || isNaN(amount) || amount <= 0) {
            alert("Vui lòng nhập số tiền hợp lệ!");
            return;
        }
        try {
            const data = await createTransaction(userId, amount, paymentMethod);
            if (data.redirectUrl) {
                window.location.href = data.redirectUrl;
            }
        } catch (error) {
            alert("Có lỗi xảy ra trong quá trình tạo giao dịch!");
        }
    };

    return (
        <div>
            <h2>Nạp Tiền Vào Tài Khoản</h2>
            <div>
                <label>
                    Nhập số tiền (VNĐ):
                    <input
                        type="number"
                        value={amount}
                        onChange={(e) => setAmount(e.target.value)}
                        placeholder="Nhập số tiền..."
                    />
                </label>
            </div>
            <div>
                <label>
                    Chọn phương thức thanh toán:
                    <select value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value)}>
                        <option value="PAYPAL">PayPal</option>
                        <option value="VNPAY">VNPAY</option>
                    </select>
                </label>
            </div>
            <button onClick={handleDeposit}>Nạp Tiền</button>
        </div>
    );
};

export default DepositMoney;
