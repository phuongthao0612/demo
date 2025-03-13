import React from "react";
import DepositMoney from "./components/DepositMoney";
import TransactionHistory from "./components/TransactionHistory";

function App() {
  const userId = 1; // Giả sử user có id = 1
  return (
      <div className="App">
        <h1>Hệ Thống Nạp Tiền Đấu Giá</h1>
        <DepositMoney userId={userId} />
        <TransactionHistory userId={userId} />
      </div>
  );
}

export default App;
