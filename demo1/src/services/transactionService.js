import axios from "axios";

const API_URL = "http://localhost:8080/api/transactions";

export const createTransaction = async (userId, amount, paymentMethod) => {
    const response = await axios.post(`${API_URL}/create`, { userId, amount, paymentMethod });
    return response.data;
};

export const getUserTransactions = async (userId) => {
    const response = await axios.get(`${API_URL}/user/${userId}`);
    return response.data;
};
