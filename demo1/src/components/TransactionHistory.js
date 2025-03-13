import { useEffect, useState } from "react";
import { getUserTransactions } from "../services/transactionService";

const TransactionHistory = ({ userId }) => {
    const [transactions, setTransactions] = useState([]);

    useEffect(() => {
        const fetchTransactions = async () => {
            try {
                const data = await getUserTransactions(userId);
                setTransactions(data);
            } catch (error) {
                console.error("Error fetching transactions", error);
            }
        };
        fetchTransactions();
    }, [userId]);

    return (
        <div>
            <h2>Lịch Sử Giao Dịch</h2>
            <table border="1">
                <thead>
                <tr>
                    <th>Số tiền</th>
                    <th>Phương thức</th>
                    <th>Trạng thái</th>
                    <th>Thời gian</th>
                </tr>
                </thead>
                <tbody>
                {transactions.map((t, index) => (
                    <tr key={index}>
                        <td>{t.amount}</td>
                        <td>{t.paymentMethod}</td>
                        <td>{t.status}</td>
                        <td>{new Date(t.createdAt).toLocaleString()}</td>
                    </tr>
                ))}
                </tbody>
            </table>
        </div>
    );
};

export default TransactionHistory;
