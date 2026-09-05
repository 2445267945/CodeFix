import { createRouter, createWebHistory } from 'vue-router'

const routes = [
    {
        path: '/',
        name: 'Workspace',
        component: () => import('../views/Workspace.vue')
    },

    // 兼容已经存在的 Task URL
    {
        path: '/tasks/:taskId',
        name: 'WorkspaceTask',
        component: () => import('../views/Workspace.vue'),
        props: true
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

export default router