import { useEffect, useState } from 'react';
import apiClient from '../api/axiosConfig';
import type { LedgerTransaction } from '../types';

export default function TransactionHistory({ refreshTrigger }: { refreshTrigger: number }) {
    const [transactions, setTransactions] = useState<LedgerTransaction[]>([]);

    useEffect(() => {
        const fetchTransactions = async () => {
            try {
                const response = await apiClient.get('/ledger/transactions');
                setTransactions(response.data);
            } catch (error) {
                console.error('Failed to fetch transactions:', error);
            }
        };

        fetchTransactions();
    }, [refreshTrigger]);

    return (
        <div style={{ marginTop: '2rem' }}>
            <h3>Transaction History</h3>
            {transactions.length === 0 ? (
                <p>No transactions found.</p>
            ) : (
                <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                    <thead>
                    <tr>
                        <th style={{ borderBottom: '1px solid #ccc', padding: '0.5rem' }}>ID</th>
                        <th style={{ borderBottom: '1px solid #ccc', padding: '0.5rem' }}>Amount</th>
                        <th style={{ borderBottom: '1px solid #ccc', padding: '0.5rem' }}>Status</th>
                        <th style={{ borderBottom: '1px solid #ccc', padding: '0.5rem' }}>Date</th>
                    </tr>
                    </thead>
                    <tbody>
                    {transactions.map((tx) => (
                        <tr key={tx.id}>
                            <td style={{ borderBottom: '1px solid #eee', padding: '0.5rem' }}>{tx.id}</td>
                            <td style={{ borderBottom: '1px solid #eee', padding: '0.5rem' }}>${tx.amount.toFixed(2)}</td>
                            <td style={{ borderBottom: '1px solid #eee', padding: '0.5rem' }}>{tx.status}</td>
                            <td style={{ borderBottom: '1px solid #eee', padding: '0.5rem' }}>
                                {new Date(tx.timestamp).toLocaleString()}
                            </td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            )}
        </div>
    );
}